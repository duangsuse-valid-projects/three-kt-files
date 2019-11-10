/** Optimization operations prepared for optimizing scanner instances */
interface FeederOptimization<E> {
  /** Scan string, inline document, identifiers... */
  fun takeUntilElem(term_set: Set<E>): Slice<E>
  /** Skip whitespace, inline document, ... */
  fun dropWhileElem(drop_set: Set<E>)
}

open class Feeder<E>(protected val inputs: Slice<E>,
                     private val sliceIter: FinalConsumeIter<E>): BulkPeekStream<E>, FeederOptimization<E>,
      FiniteStream by sliceIter, Sized by sliceIter, Resetable, Iterable<E> {
  constructor(inputs: Slice<E>): this(inputs, inputs.resetIterator())
  constructor(list: List<E>): this(Slice.of(list))
  constructor(ary: Array<E>): this(Slice.of(ary))

  override val peek get() = lastItem
  override fun consume(): E = peek.alsoDo { checkIsEOS()
    Try<IndexOutOfBoundsException, Unit> { lastItem = sliceIter.next() }
       ?: run { sliceIter.oneMore = false; --sliceIter.position /* for this 'abnormal' call */ }
  }

  override fun take(n: Cnt): BulkPeekStream.Viewport<E> { checkIsEOS()
    val peekPos = sliceIter.position.dec(); val newPos = peekPos+n
    if (newPos > sliceIter.size) throw sliceIter.eofError(sliceIter.position, "take $n")

    val view = inputs[peekPos until newPos]
    return object : BulkPeekStream.Viewport<E>, Slice<E> by view {
      override fun consume(): Slice<E> {
        sliceIter.position = newPos
        this@Feeder.consume(); return view }
    }
  }

  private fun checkIsEOS() = if (isEnd) throw sliceIter.eofError(sliceIter.position) else Unit

  private var lastItem: E
  private var oldLastItem: E
  init {
    check(inputs.isNotEmpty, constant("Input should not be empty"))
    lastItem = sliceIter.next(); oldLastItem = lastItem }

  /** Iterator is rewritten, since [sliceIter]`.next()` is called when `init` (acquire first item) */
  override fun iterator() = object : Iterator<E> {
    override fun hasNext() = !isEnd
    override fun next() = consume()
  }

  override fun mark() = sliceIter.mark().also { oldLastItem = lastItem; check(sliceIter.oneMore, EOF_MARK) }
  override fun reset() = sliceIter.reset().also { lastItem = oldLastItem; sliceIter.oneMore = true }

  override fun toString() = "Feeder($peek: $sliceIter)"

  override fun takeUntilElem(term_set: Set<E>): Slice<E> = this.takeWhile { it !in term_set }.toList().let(Slice.Factory::of)
  override fun dropWhileElem(drop_set: Set<E>) = this.dropWhile { it in drop_set }

  companion object Factory {
    fun of(str: CharSequence): Feeder<Char> = Feeder(Slice.of(str))
    private inline val EOF_MARK get() = constant("Marking at EOF")
  }
}
