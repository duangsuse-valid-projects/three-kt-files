import kotlin.native.concurrent.ThreadLocal

typealias Atom = Int
typealias Reduce<T> = (T, T) -> T
typealias Rewrite<T> = (T) -> T
typealias FoldRight<T, R> = (T, R) -> R

abstract class IdMap<I, out T>(private val map: Map<I, T>): Map<I, T> by map {
  constructor(values: Iterable<T>, index: (T) -> I): this(values.map { index(it) to it }.toMap())
  constructor(array: Array<T>, index: (T) -> I): this(array.asIterable(), index)
}

interface Peek<out T> { val peek: T; fun consume(): T }
object PeekEnd: NoSuchElementException("no more")
open class Peeking<T>(private val iterator: Iterator<T>): Peek<T> {
  private var lastItem = iterator.next(); private var tailConsumed = false
  override val peek get() = lastItem
  override fun consume(): T = if (iterator.hasNext())
    lastItem.also { lastItem = iterator.next() }
  else if (!tailConsumed)
    lastItem.also { tailConsumed = true }
  else throw PeekEnd
}

fun <T, R> Peek<T>.takeIgnoreEOS(initial: R, op: (R, T) -> R?): R {
  var accumulator = initial
  try { while (true) {
    op(accumulator, peek)?.let { consume(); accumulator = it } ?: break
  } } catch (_: PeekEnd) { return accumulator }
  return accumulator
}
fun <T> Peek<T>.consumeIfHas(item: T): Boolean = peek == item && consume() == item

open class Stack<T>(protected val backend: MutableList<T>) {
  protected val top get() = backend.lastIndex
  val isEmpty get() = backend.isEmpty()
  fun add(item: T) { backend.add(item) }
  fun pop(): T = backend.removeAt(top)
  fun clear() { backend.clear() }
}
class Recursion<T>: Stack<T>(mutableListOf()) {
  fun mapTop(op: Rewrite<T>) { backend[top] = backend[top].let(op) }
  fun <R> reduce(base: R, backtrace: FoldRight<T, R>): R {
    var accumulator = base
    while (!isEmpty)
      { accumulator = backtrace(pop(), accumulator) }
    return accumulator
  }
}

abstract class CalcLex(input: CharIterator): Peeking<Char>(input) {
  private val digits = '0'..'9'; private val radix = 10
  private fun digitToInt(c: Char) = c - '0'

  // 10, 200, 999
  protected open fun scanAtom(): Atom { scanSpaces()
    val sign = consumeIfHas('-')
    check(peek in digits) {"$peek not decimal digit"}
    return takeIgnoreEOS(0) { ac, d ->
      d.takeIf(digits::contains)?.let { ac * radix + digitToInt(it) }
    }.let { if (sign) -it else it }
  }

  protected fun scanInfix(): Calc.Op? { scanSpaces()
    return if (peek in Calc.Op.keys) Calc.Op[consume()] else null
  }

  private val spaces = setOf(' ', '\t', '\n', '\r')
  private fun scanSpaces(): StringBuilder = takeIgnoreEOS(StringBuilder())
    { sb, c -> c.takeIf { it in spaces }?.let(sb::append) }
}

class Calc(input: CharIterator): CalcLex(input) {
  enum class Op(val levL: Int, val levR: Int, val infix: Char, val join: Reduce<Atom>) {
    Add(2, '+', Int::plus), Sub(2, '-', Int::minus),
    Mul(1, '*', Int::times), Div(1, '/', Int::div),
    Exc(0, '!', Int::rem);
    constructor(infix_l: Int, infix: Char, join: Reduce<Atom>):
      this(infix_l*2, infix_l*2+1, infix, join)
    companion object: IdMap<Char, Op>(values(), Op::infix)
  }
  fun eval(): Atom = scanExpr()
  private fun scanExpr(): Atom = infixChain1(scanAtom())

  private fun infixChain1(base: Atom, op_left: Op? = null): Atom {
    val op1 = op_left ?: scanInfix() ?: return base  //'+' | 1+(2*3)...
    val rhs1 = scanAtom() //'2'
    val op2 = scanInfix() ?: return op1.join(base, rhs1)
    return when {
      op1 <= op2 -> infixChain1(op1.join(base, rhs1), op2)
      op1  > op2 -> op1.join(base, infixChain1(rhs1, op2))
      else -> throw IllegalStateException()
    }
  }

  sealed class AssocContext {
    class Base(var expr: Atom): AssocContext()
      { fun map(op: Rewrite<Atom>): Base = Base(op(expr)) }
    class Tail(val join: Reduce<Atom>): AssocContext()
  }
  @ThreadLocal
  private val infixChainStack = Recursion<AssocContext>()
  internal fun infixChain(): Atom {
    val atomAt0 = scanAtom()
    var op1 = scanInfix() ?: return atomAt0
    val stack = infixChainStack
    fun top(): Atom = (stack.pop() as AssocContext.Base).expr
    stack.add(AssocContext.Base(atomAt0))
    while (true) {
      val rhs1 = scanAtom()
      val rewriteJoin: Rewrite<AssocContext>
        = { l -> (l as AssocContext.Base).map { op1.join(it, rhs1) } }
      val op2 = scanInfix() ?: run { stack.mapTop(rewriteJoin)
        ; null } ?: break
      when { //precedence lower-first assoc
        op1.levL <= op2.levR -> stack.mapTop(rewriteJoin)
        op1.levL  > op2.levR -> {
          stack.add(AssocContext.Tail(op1.join))
          stack.add(AssocContext.Base(rhs1))
        }
      }
      op1 = op2
    }
    return stack.reduce(top()) { op, r ->
      (op as AssocContext.Tail).join(top(), r) }
  }

  override fun scanAtom(): Atom = if (consumeIfHas('(')) {
    val inner = scanExpr()
    check(consumeIfHas(')')) {"Missing closing paren"}
    inner
  } else super.scanAtom()
}