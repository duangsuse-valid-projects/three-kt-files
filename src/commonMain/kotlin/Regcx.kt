interface Peeks<out T> {
  val peek: T
  fun consume(): T
}

class IterPeeks<out T>(private val iterator: Iterator<T>): Peeks<T> {
  override var peek: @UnsafeVariance T = iterator.next()
    private set
  override fun consume(): T = peek.also {
    if (iterator.hasNext()) peek = iterator.next()
    else if (!lastItemConsumed) lastItemConsumed = true
    else throw NoSuchElementException()
  }
  private var lastItemConsumed = false
}

abstract class EnumIndexer<in K, out V>(private val map: Map<K, V>) {
  constructor(entries: Array<V>, key: (V) -> K): this(entries.map { key(it) to it }.toMap())
  operator fun contains(key: K): Boolean = key in map
  operator fun get(key: K): V? = map[key]
}

interface Matcher<in T, out R> {
  fun match(input: Peeks<T>): R?
}
interface PositiveMatcher<in T, out R>: Matcher<T, R> {
  override fun match(input: Peeks<T>): R
}

open class Seq<T, R>(private val sub: Collection<Matcher<T, R>>): Matcher<T, List<R>> {
  constructor(vararg sub: Matcher<T, R>): this(sub.toList())
  override fun match(input: Peeks<T>): List<R>? {
    val res = mutableListOf<R>()
    for (m in sub) m.match(input)?.let(res::add) ?: break
    return res.takeIf { it.size == sub.size }
  }
}
open class Or<T, R>(private val sub: Iterable<Matcher<T, R>>): Matcher<T, R> {
  constructor(vararg sub: Matcher<T, R>): this(sub.asIterable())
  override fun match(input: Peeks<T>): R? {
    for (m in sub) m.match(input)?.let { return it }
    return null
  }
}
open class Quantify<T, R>(private val sub: Matcher<T, R>, private val bound: IntRange): Matcher<T, List<R>> {
  override fun match(input: Peeks<T>): List<R>? {
    val res = mutableListOf<R>(); var count = 0
    while (count != bound.last.inc()) {
      sub.match(input)?.let(res::add) ?: break
      ++count
    }
    return res.takeIf { count in bound }
  }
}
private inline val SOME get() = 1..Int.MAX_VALUE
private inline val MANY get() = 0..Int.MAX_VALUE

open class Satisfy<T>(private val predicate: Predicate<T>): Matcher<T, T> {
  override fun match(input: Peeks<T>): T? {
    return input.peek.takeIf(predicate)?.also { input.consume() }
  }
}
sealed class Maybe<out T>(open val item: T? = null) {
  data class Some<T>(override val item: T): Maybe<T>()
  object None: Maybe<Nothing>()
  fun <R> map(op: (T) -> R): Maybe<R> = wrap(item?.let(op))
  fun <R> flatMap(op: (T) -> Maybe<R>): Maybe<R> = item?.let(op)?.let(::flatten1)?.let(::Some) ?: None
  companion object {
    fun <A> flatten1(functor: Maybe<A>): A? = functor.item
    fun <A> wrap(x: A?): Maybe<A> = x?.let(::Some) ?: None
  }
}
fun <T> T?.toMaybe(): Maybe<T> = Maybe.wrap(this)
class Optional<T, R>(private val sub: Matcher<T, R>): PositiveMatcher<T, Maybe<R>> {
  override fun match(input: Peeks<T>): Maybe<R> = sub.match(input).toMaybe()
}
////
open class Elem<T>(private val set: Set<T>): Satisfy<T>({ it in set })
open class NotElem<T>(private val set: Set<T>): Satisfy<T>({ it !in set })
open class Item<T>(private val item: T): Satisfy<T>({ it == item })
open class SingleItem<T>: Satisfy<T>({ true })
////
class PeekBoundary<T>: PositiveMatcher<T, T> {
  override fun match(input: Peeks<T>): T = input.peek
}
abstract class Contextual<in IN, T, out R>(private val sub: Matcher<IN, T>): Matcher<IN, R> {
  protected abstract fun after(res: T): Matcher<IN, R>
  override fun match(input: Peeks<IN>): R? = sub.match(input)?.let { after(it).match(input) }
}
abstract class Compose<in IN, T, out R>(private val sub: Matcher<IN, T>): Matcher<IN, R> {
  protected abstract fun transform(res: T): R?
  override fun match(input: Peeks<IN>): R? = sub.match(input)?.let(::transform)
}
class Deferred<in T, out R>(private val sub: Producer<Matcher<T, R>>): Matcher<T, R> {
  override fun match(input: Peeks<T>): R? = sub().match(input)
}
////
typealias Binding<R> = MutableMap<String, R>
class BindSeq<T, R>(sub: (Binding<R>) -> Collection<Matcher<T, R>>, named: Binding<R> = mutableMapOf()): Seq<T, R>(sub(named))
abstract class BindingContext<R>(protected val binding: Binding<R>): Matcher<R, R>
class Group<R>(binding: Binding<R>, private val name: String, private val sub: Matcher<R, R>): BindingContext<R>(binding) {
  override fun match(input: Peeks<R>): R? {
    return sub.match(input)?.also { binding[name] = it }
  }
}
class Backref<R>(binding: Binding<R>, private val name: String): BindingContext<R>(binding) {
  override fun match(input: Peeks<R>): R? = binding[name].takeIf { input.peek == it }
}
////
enum class 猫 { 黑猫, 白猫, 花猫, 龙猫 }

typealias 猫录 = 猫?
typealias 猫流 = IterPeeks<猫录>
object Anything: SingleItem<猫录>()
object AnyCat: Satisfy<猫录>({ it != null })
object NilRecord: Item<猫录>(null)
inline val PeekCat get() = PeekBoundary<猫录>()

class LexerError(message: String? = null): Error(message)
abstract class Lexer<out TOKEN: Any>(private val input: Peeks<Char>) {
  fun tokens(): Sequence<TOKEN> = generateSequence { nextToken() }
  open fun nextToken(): TOKEN? = try { token.match(input) } catch (_: NoSuchElementException) {null}
  abstract val token: Matcher<Char, TOKEN>
  companion object Factory {
    fun <IN, TOKEN: Any> make(c: (Peeks<IN>) -> Lexer<TOKEN>, input: Iterator<IN>) = c(IterPeeks(input))
    fun <TOKEN: Any> make(c: (Peeks<Char>) -> Lexer<TOKEN>, input: CharIterator) = c(IterPeeks(input))
    fun <TOKEN: Any> make(c: (Peeks<Char>) -> Lexer<TOKEN>, input: CharSequence) = c(IterPeeks(input.iterator()))
  }
}
enum class Token(val idChar: Char) {
  B('B'), W('W'), H('H'), L('L'),
  LeftBR('{'), RightBR('}'),
  LeftSQ('['), RightSQ(']'),
  LeftP('('), RightP(')'),
  HILL('^'), MID('|'), COMMA(','),
  SOME('+'), MANY('*'), OPTIONAL('?'),
  AnyCat('.'), AnyNil('✖'), PeekCat('x'),
  Begin('^'), End('$'), White(' ');
  companion object Index: EnumIndexer<Char, Token>(values(), Token::idChar)
}
class 分词(input: Peeks<Char>): Lexer<Token>(input) {
  override val token: Matcher<Char, Token> = Or(ws, EnumP(Token))
  companion object Rules {
    val whiteSpace = Elem(setOf(' ', '\t', '\n', '\r'))
  }
  object ws: Compose<Char, List<Char>, Token>(Quantify(whiteSpace, SOME)) {
    override fun transform(res: List<Char>): Token = Token.White
  }
  class EnumP<I, T>(private val indexer: EnumIndexer<I, T>): Compose<I, I, T>(Satisfy { it in indexer }) {
    override fun transform(res: I): T = indexer[res]!!
  }
}
object 解析 {
  val Cat = Elem(setOf(Token.B, Token.W, Token.H, Token.L))
  val Factor0 = Or(Elem(setOf(Token.AnyCat, Token.AnyNil, Token.PeekCat)), Cat)
  val Factor = Deferred { Or(Factor0) }
}
