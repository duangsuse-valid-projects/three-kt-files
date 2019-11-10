//import kotlin.contracts.*
//@UseExperimental(ExperimentalContracts::class)

typealias Cnt = Int
typealias Idx = Int

typealias Predicate<T> = (T) -> Boolean
typealias Producer<R> = () -> R
typealias Operation = Producer<*>

expect fun assert(p: Boolean)
fun impossible(): Nothing { assert(false); throw Error() }
inline fun <T> T.alsoDo(crossinline op: Operation): T = also { op() }
fun <V> constant(value: V): Producer<V> = { value }
inline fun <reified EX : Throwable, R : Any> Try(op: Producer<R>): R? = try { op() }
  catch (e: Throwable) { if (e is EX) null else (throw e)  }

interface Sized {
  val size: Cnt get
  companion object Factory {
    fun of(c: Collection<*>) = object : Sized
      { override val size get() = c.size }
    fun of(xs: Array<*>) = object : Sized
      { override val size: Cnt get() = xs.size }
  }
}
inline val Sized.isEmpty get() = size == 0
inline val Sized.isNotEmpty get() = !isEmpty
inline val Sized.lastIndex get() = size.dec()
inline val Sized.indices get() = 0..lastIndex

/** A type with ability to restore its state
 *  + [reset] cannot be called before calling [mark] for first time
 *  + [reset] should be called before calling [mark] 2nd time */
interface Resetable {
  fun mark() fun reset()
  /** __NOTE__: [mark] cannot be called when [isMarking] */
  interface Stated: Resetable { val isMarking: Boolean get }
}
/** Do operation [op] with protected state of [this], note that [op] is __NOT__ reentrant */
//@ExperimentalContracts
fun <R> Resetable.positional(op: Producer<R>): R {
  //contract { callsInPlace(op, InvocationKind.EXACTLY_ONCE) }
  if (this is Resetable.Stated) assert(!this.isMarking)
  return mark().let { op().alsoDo(::reset) }
}

fun <A, B, R> Iterable<A>.zipWith(other: Iterable<B>, f: (A, B) -> R): Iterable<R> = Iterable {
  object : Iterator<R> {
    val xs = this@zipWith.iterator()
    val ys = other.iterator()
    override fun hasNext() = xs.hasNext() && ys.hasNext()
    override fun next() = f(xs.next(), ys.next())
  }
}
