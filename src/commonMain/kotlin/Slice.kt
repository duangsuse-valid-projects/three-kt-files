interface MutableSlice<E>: Slice<E> {
  operator fun set(index: Idx, value: E)
  override operator fun get(indices: IntRange): MutableSlice<E>

  data class OfArray<E>(private val ary: Array<E>) : MutableSlice<E> {
    override val size: Cnt get() = ary.size
    override fun get(index: Idx): E = ary[index]
    override fun get(indices: IntRange): MutableSlice<E> = OfArray(ary.sliceArray(indices))
    override fun set(index: Idx, value: E) { ary[index] = value }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other == null || this::class != other::class) return false
      other as OfArray<*>
      return ary.contentEquals(other.ary)
    }
    override fun hashCode(): Int = ary.contentHashCode()
    override fun toString(): String = "Slice[${this.ary.joinToString()}]"
  }
  data class OfList<E>(private val list: MutableList<E>) : MutableSlice<E> {
    override val size get() = list.size
    override operator fun get(index: Int) = list[index]
    override fun get(indices: IntRange): MutableSlice<E> = OfList(list.slice(indices).toMutableList())
    override operator fun set(index: Int, value: E) { list[index] = value }
    override fun toString(): String = "Slice.of(mut ${this.list})"
  }
}

interface Slice<out E>: Sized, ResetIterable<E> {
  operator fun get(index: Idx): E
  operator fun get(indices: IntRange): Slice<E>

  override fun iterator(): Iterator<E> = object : Iterator<E> {
    private var position: Idx = 0
    override fun hasNext() = position != size
    override fun next() = this@Slice[position++]
  }
  override fun resetIterator() = object : FinalConsumeIter<E> {
    override var position: Idx = 0
    override fun hasNext() = position < size //!in setOf(size, size.inc())
    override fun next() = this@Slice[position++]

    private var oldPosition: Idx = (-1)
    override fun mark() { oldPosition = position }
    override fun reset() { position = oldPosition }

    override var oneMore = true
    override val isEnd get() = !oneMore && !hasNext()
    override val size = this@Slice.size
  }

  data class OfList<out E>(private val list: List<E>) : Slice<E> {
    override val size get() = list.size
    override operator fun get(index: Int): E = list[index]
    override fun get(indices: IntRange): Slice<E> = of(list.slice(indices))
    override fun toString(): String = "Slice.of($list)"
  }
  data class OfCharSequence(private val char_seq: CharSequence) : Slice<Char> {
    override val size get() = char_seq.length
    override fun get(index: Idx): Char = char_seq[index]
    override fun get(indices: IntRange): Slice<Char> = of(char_seq.subSequence(indices))
    override fun toString(): String = "Slice`$char_seq'"
  }
  companion object Factory {
    fun <T> of(ary: Array<T>): MutableSlice<T> = MutableSlice.OfArray(ary)
    fun <T> of(list: List<T>): Slice<T> = OfList(list)
    fun <T> of(list: MutableList<T>): MutableSlice<T> = MutableSlice.OfList(list)
    fun of(char_seq: CharSequence): Slice<Char> = OfCharSequence(char_seq)
  }
}
