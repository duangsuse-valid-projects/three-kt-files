interface DiffAlgorithm {
  fun <E> diff(xs: Feeder<E>, ys: Feeder<E>): Differences<E>

  data class Differences<out T>(val changes: List<Change<T>>) {
    constructor(): this(emptyList())
    override fun toString() = changes.joinToString(Change.partSep) }

  sealed class Change<out T> {
    data class Same<T>(val part: List<T>) : Change<T>()
      {  override fun toString() = part.joinToString(sep) }
    data class Removed<T>(val deleted: List<T>) : Change<T>()
      { override fun toString() = "(${deleted.joinToString(sep)})" }
    data class Inserted<T>(val added: List<T>) : Change<T>()
      { override fun toString() = "[${added.joinToString(sep)}]" }

    companion object { var sep = ","; var partSep = " " }
  }
}

typealias Change<T> = DiffAlgorithm.Change<T>
object Diff : DiffAlgorithm {
  private inline val Any?.isAny: Boolean get() = this != null
  private fun  <E> MutableList<E>.shadow(): List<E> = this.asIterable().toList()
    .alsoDo { this@shadow.clear() }

  fun <E> diffSeq(xs: Feeder<E>, ys: Feeder<E>) = sequence {
    fun appendSome(cast: (List<E>) -> Change<E>) =
      fun(col: Collection<E>) = col.takeIf { it.isNotEmpty() }?.toList()?.let(cast)
    val same = appendSome { DiffAlgorithm.Change.Same(it) }
    val inserted = appendSome { DiffAlgorithm.Change.Inserted(it) }
    val deleted = appendSome { DiffAlgorithm.Change.Removed(it) }

    val equalPart = mutableListOf<E>()
    while (!xs.isEnd && !ys.isEnd) {
      if (xs.peek == ys.peek)
        { equalPart.add(xs.consume().alsoDo(ys::consume)); continue }
      same(equalPart.shadow())?.let { yield(it) }
      var del: Change<E>? = null; var ins: Change<E>?

      val xsPossibleSuccs = xs.positional { xs.toSet() }
      ys.mark() //for EOF-rescue when no common succeed found
      try { ins = ys.takeWhile { it !in xsPossibleSuccs }.toList().let(inserted) } catch (_: StreamEnd) {
        xs.drop(xsPossibleSuccs.size); ys.reset()
        del = xsPossibleSuccs.toList().let(deleted)
        ins = ys.toList().let(inserted)
        assert(xs.isEnd && ys.isEnd) // no `common' succeed found
      }
      if (del == null) {
        val commonSucc = ys.peek
        del = xs.takeWhile { it != commonSucc }.toList().let(deleted)
        assert(xs.peek == ys.peek)
      }
      if(del.isAny) yield(del!!); if(ins.isAny) yield(ins!!)
    }

    same(equalPart)?.let { yield(it) } // Same(...) when length does not match "abc" vs. "ab"
    when { // with tail-sequence of Change(...)
      xs.isEnd -> inserted(ys.toList())?.let { yield(it) } // "abcd" vs. "abc"
      ys.isEnd -> deleted(xs.toList())?.let { yield(it) } // "abc" vs. "abcd"
    }
  }

  fun <E> diff(s0: Slice<E>, s1: Slice<E>): DiffAlgorithm.Differences<E> {
    val s1Any = s1.isNotEmpty
    val s0Any = s0.isNotEmpty
    return when {
      (s0Any && s1Any) -> diff(Feeder(s0), Feeder(s1))
      (!s0Any && s1Any) -> DiffAlgorithm.Differences(
          listOf(DiffAlgorithm.Change.Inserted(s1.toList())))
      (s0Any && !s1Any) -> DiffAlgorithm.Differences(
          listOf(DiffAlgorithm.Change.Removed(s0.toList())))
      (!s0Any && !s1Any) -> DiffAlgorithm.Differences()
      else -> impossible()
    }
  }
  override fun <E> diff(xs: Feeder<E>, ys: Feeder<E>): DiffAlgorithm.Differences<E>
      = diffSeq(xs, ys).toList().let(DiffAlgorithm::Differences)
  fun <E> diff(xs: List<E>, ys: List<E>): DiffAlgorithm.Differences<E> = diff(Slice.of(xs), Slice.of(ys))
  fun diff(a: String, b: String): DiffAlgorithm.Differences<Char> = diff(Slice.of(a), Slice.of(b))

  fun tokenDiff(t0: String, t1: String, vararg sep: Char = C_WHITE) = diff(t0.split(*sep), t1.split(*sep))
  fun tokenDiff(t0: String, t1: String, regex: Regex) = diff(t0.split(regex), t1.split(regex))
  private val C_WHITE = charArrayOf(' ', '\b', '\t', '\n', '\r')
}
