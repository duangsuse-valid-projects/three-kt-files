interface FiniteStream { val isEnd: Boolean get }
interface IndexedIterator: Sized { val position: Idx }
interface MutableIndexedIterator: IndexedIterator { override var position: Idx }

data class StreamEnd(val position: Idx, val max: Idx? = null, val info: String?): Error() {
  override val message: String? get() = "$position${maxTag()}${infoTag()}"
  private fun maxTag() = max?.let { "(bound 0..$max)" } ?: ""
  private fun infoTag() = if (!info.isNullOrEmpty()) ": $info" else ""
}
fun Sized.eofError(position: Idx, info: String? = null) = StreamEnd(position, lastIndex, info)

expect interface PeekStream<out E> {
  val peek: E get
  fun consume(): E
}
interface BulkPeekStream<out E>: PeekStream<E> {
  fun take(n: Cnt): Viewport<E>
  interface Viewport<out E>: Slice<E>
    { fun consume(): Slice<E> }
}

interface ResetIterator<out E> : Iterator<E>, Resetable
interface ResetIterable<out E> : Iterable<E> {
  fun resetIterator(): ResetIterator<E>
  override fun iterator(): Iterator<E> = resetIterator()
}

/** Helper for 'peek-1' streams */
interface FinalConsumeIter<out E> : ResetIterator<E>, FiniteStream, MutableIndexedIterator
  { var oneMore: Boolean }

fun <E> PeekStream<E>.takeWhile(predicate: Predicate<E>) = sequence {
  while (predicate(peek))
    { yield(consume()) }
}

fun <E> PeekStream<E>.dropWhile(predicate: Predicate<E>) {
  while (predicate(peek)) consume()
}
