import NumEqualize.balance
import NumEqualize.balanceForBitwise
import NumEqualize.balanceLossy
import kotlin.math.sign
import NumEqualizer.Level

typealias Num = Number
typealias BinLevel = Int
typealias Order = Int
typealias LevelPicker = (Level, Level) -> Level

typealias NumOps = Ops<out Num>
typealias BitOps = Ops.Bitwise<out Num>

interface NumEqualizer {
  /** __DO NOT__ reorder variants, keep ascending by [lev] */
  enum class Level(private val lev: BinLevel): Comparable<Level> {
    Int8 (+4),
    Int16(+3),
    Int32(+2),
    Int64(+1),
    Rat32(-1),
    Rat64(-2);
    enum class Family {
      Int, Real;
      override fun toString() = name.toLowerCase()
    }
    val family: Family get() = if (lev < 0) Family.Real else Family.Int
    val isIntegral: Boolean get() = this.family == Family.Int
    fun familyXor(other: Level) = this.lev.sign != other.lev.sign
  }
  fun levelOf(n: Num): Level
  fun forLevel(level: Level): NumOps
  fun operator(n: Num): NumOps
  fun operator(n: Int): Ops.Bitwise<Int>
  fun operator(n: Long): Ops.Bitwise<Long>
  fun balance(a: Num, b: Num, picker: LevelPicker): NumOps
}

object NumEqualize: NumEqualizer {
  override fun levelOf(n: Num): Level = when (n) {
    is Double -> Level.Rat64
    is Float -> Level.Rat32
    is Long -> Level.Int64
    is Int -> Level.Int32
    is Short -> Level.Int16
    is Byte -> Level.Int8
    else -> throw Error("Cannot qualify level for ${n::class} instance")
  }
  override fun forLevel(level: Level) = Equalizer.equalizerMap[level] ?: throw Error("Missing $level equalizer")

  private fun <O: Comparable<O>> cmpMax(a: O, b: O): O = if (a > b) a else b
  private val lossless: LevelPicker = { l, r ->
    check(!l.familyXor(r)) { "Numeric of level $l / $r has different families (${l.family}/${r.family})" }
    cmpMax(l, r) }
  private val bitwise: LevelPicker = { l, r ->
    check(l.isIntegral && r.isIntegral)
      { "Non-integral Numeric operands $l (${l.family}) / $r (${r.family})" }
    cmpMax(Level.Int32, cmpMax(l, r))
  }

  override fun operator(n: Num): NumOps = forLevel(levelOf(n))
  override fun operator(n: Int): Ops.Bitwise<Int> = Equalizer.Int32
  override fun operator(n: Long): Ops.Bitwise<Long> = Equalizer.Int64

  override fun balance(a: Num, b: Num, picker: LevelPicker)
      = forLevel(picker(levelOf(a), levelOf(b)))
  fun balance(a: Num, b: Num) = balance(a, b, lossless)
  fun balanceLossy(a: Num, b: Num) = balance(a, b, ::cmpMax)
  fun balanceForBitwise(a: Num, b: Num) = balance(a, b, bitwise)
}

abstract class Ops<N: Num> {
  protected inline fun <M> coercedO(crossinline op: N.(N) -> M): (Number, Number) -> M
      = { a, b -> op(coerce(a), coerce(b)) }
  protected inline fun <M, R> coercedIO(crossinline op: N.(M) -> R): (Number, M) -> R
      = { a, i -> op(coerce(a), i) }
  protected inline fun coercedUnary(crossinline op: N.() -> N): (Number) -> N
      = { n -> op(coerce(n)) }

  protected abstract val coerce: Number.() -> N
  protected abstract val opAdd: N.(N) -> N
  protected abstract val opSub: N.(N) -> N
  protected abstract val opMul: N.(N) -> N
  protected abstract val opDiv: N.(N) -> N
  protected abstract val opMod: N.(N) -> N
  protected abstract val opNeg: N.() -> N
  protected abstract val opCmp: N.(N) -> Order

  protected abstract val opSucc: N.() -> N
  protected abstract val opPred: N.() -> N

  fun plus(a: Num, b: Num) = coercedO(opAdd)(a, b)
  fun minus(a: Num, b: Num) = coercedO(opSub)(a, b)
  fun times(a: Num, b: Num) = coercedO(opMul)(a, b)
  fun div(a: Num, b: Num) = coercedO(opDiv)(a, b)
  fun mod(a: Num, b: Num) = coercedO(opMod)(a, b)
  fun unaryMinus(n: Num) = coercedUnary(opNeg)(n)
  fun compareTo(a: Num, b: Num) = coercedO(opCmp)(a, b)

  fun inc(n: Num) = coercedUnary(opSucc)(n)
  fun dec(n: Num) = coercedUnary(opPred)(n)

  open class Instance<N: Num> constructor(
      override val coerce: Number.() -> N,
      override val opAdd: N.(N) -> N, override val opSub: N.(N) -> N,
      override val opMul: N.(N) -> N, override val opDiv: N.(N) -> N,
      override val opMod: N.(N) -> N, override val opNeg: N.() -> N,
      override val opCmp: N.(N) -> Order,
      override val opSucc: N.() -> N, override val opPred: N.() -> N): Ops<N>()

  abstract class Bitwise<N: Num>: Ops<N>() {
    protected abstract val opAnd: N.(N) -> N
    protected abstract val opOr: N.(N) -> N
    protected abstract val opXor: N.(N) -> N
    protected abstract val opInverse: N.() -> N
    protected abstract val opShl: N.(Cnt) -> N
    protected abstract val opShr: N.(Cnt) -> N
    protected abstract val opUShr: N.(Cnt) -> N

    fun and(a: Num, b: Num) = coercedO(opAnd)(a, b)
    fun or(a: Num, b: Num) = coercedO(opOr)(a, b)
    fun xor(a: Num, b: Num) = coercedO(opXor)(a, b)
    fun inv(n: Num) = coercedUnary(opInverse)(n)
    fun shl(a: Num, k: Cnt) = coercedIO(opShl)(a, k)
    fun shr(a: Num, k: Cnt) = coercedIO(opShr)(a, k)
    fun ushr(a: Num, k: Cnt) = coercedIO(opUShr)(a, k)

    open class Instance<N: Num> constructor(
        override val coerce: Number.() -> N,
        override val opAdd: N.(N) -> N, override val opSub: N.(N) -> N,
        override val opMul: N.(N) -> N, override val opDiv: N.(N) -> N,
        override val opMod: N.(N) -> N, override val opNeg: N.() -> N,
        override val opCmp: N.(N) -> Order,
        override val opSucc: N.() -> N, override val opPred: N.() -> N,
        override val opAnd: N.(N) -> N, override val opOr: N.(N) -> N,
        override val opXor: N.(N) -> N,
        override val opInverse: N.() -> N,
        override val opShl: N.(Cnt) -> N, override val opShr: N.(Cnt) -> N,
        override val opUShr: N.(Cnt) -> N): Ops.Bitwise<N>()
  }
}

object Equalizer {
  val Rat64: Ops<Double> = Ops.Instance(Number::toDouble,
      Double::plus, Double::minus, Double::times, Double::div,
      Double::rem, Double::unaryMinus, Double::compareTo, Double::inc, Double::dec)
  val Rat32: Ops<Float> = Ops.Instance(Number::toFloat,
      Float::plus, Float::minus, Float::times, Float::div,
      Float::rem, Float::unaryMinus, Float::compareTo, Float::inc, Float::dec)
  val Int64: Ops.Bitwise<Long> = Ops.Bitwise.Instance(Number::toLong,
      Long::plus, Long::minus, Long::times, Long::div,
      Long::rem, Long::unaryMinus, Long::compareTo, Long::inc, Long::dec,
      Long::and, Long::or, Long::xor, Long::inv,
      Long::shl, Long::shr, Long::ushr)
  val Int32: Ops.Bitwise<Int> = Ops.Bitwise.Instance(Number::toInt,
      Int::plus, Int::minus, Int::times, Int::div,
      Int::rem, Int::unaryMinus, Int::compareTo, Int::inc, Int::dec,
      Int::and, Int::or, Int::xor, Int::inv,
      Int::shl, Int::shr, Int::ushr)
  val Int16: Ops<Short> = Ops.Instance(Number::toShort,
      { (this+it).toShort() }, { (this-it).toShort() }, { (this*it).toShort() }, { (this/it).toShort() },
      { (this%it).toShort() }, { (-this).toShort() }, { this.compareTo(it) }, Short::inc, Short::dec)
  val Int8: Ops<Byte> = Ops.Instance(Number::toByte,
      { (this+it).toByte() }, { (this-it).toByte() }, { (this*it).toByte() }, { (this/it).toByte() },
      { (this%it).toByte() }, { (-this).toByte() }, { this.compareTo(it) }, Byte::inc, Byte::dec)
  internal val equalizerMap = mapOf<Level, NumOps>(
      Level.Rat64 to Rat64,
      Level.Rat32 to Rat32,
      Level.Int64 to Int64,
      Level.Int32 to Int32,
      Level.Int16 to Int16,
      Level.Int8 to Int8)
}

interface NumericOps {
  fun plus(a: Num, b: Num):Num fun minus(a: Num, b: Num):Num
  fun times(a: Num, b: Num):Num fun div(a: Num, b: Num):Num fun mod(a: Num, b: Num):Num
  fun unaryMinus(n: Num):Num
  fun compareTo(a: Num, b: Num):Order

  fun inc(n: Num):Num fun dec(n: Num):Num

  fun and(a: Num, b: Num):Num fun or(a: Num, b: Num):Num
  fun xor(a: Num, b: Num):Num
  fun inv(n: Num):Num
  fun shl(a: Num, k: Cnt):Num fun shr(a: Num, k: Cnt):Num fun ushr(a: Num, k: Cnt):Num
}

typealias O = Ops<*>
typealias OB = Ops.Bitwise<*>
open class AutoBalancedOps(private val balancer: (Num, Num) -> NumOps): NumericOps {
  private inline fun balanced(a: Num, op: NumOps.(Num, Num) -> Num, b: Num): Num
    = balancer(a, b).op(a, b)
  private inline fun operated(op: NumOps.(Num) -> Num, n: Num): Num
    = NumEqualize.operator(n).op(n)

  private inline fun balancedBitwise(a: Num, op: BitOps.(Num, Num) -> Num, b: Num): Num
      = balancer(a, b).asBitOps.op(a, b)
  private inline fun balancedBitwiseShlShr(a: Num, op: BitOps.(Num, Cnt) -> Num, b: Cnt): Num
      = balancer(a, b).asBitOps.op(a, b)
  private inline fun operatedBitwise(op: BitOps.(Num) -> Num, n: Num): Num
      = NumEqualize.operator(n).asBitOps.op(n)

  private inline val NumOps.asBitOps: BitOps
    get() = (this as? BitOps) ?: bitwiseFail(this@asBitOps)
  protected fun bitwiseFail(self: Any): Nothing
      = throw Error("Failed to convert equalizer of type $self ${self::class} to bitwise equalizer")

  override fun plus(a: Num, b: Num) = balanced(a, O::plus, b)
  override fun minus(a: Num, b: Num) = balanced(a, O::minus, b)
  override fun times(a: Num, b: Num) = balanced(a, O::times, b)
  override fun div(a: Num, b: Num) = balanced(a, O::div, b)
  override fun mod(a: Num, b: Num) = balanced(a, O::mod, b)
  override fun unaryMinus(n: Num) = operated(O::unaryMinus, n)
  override fun compareTo(a: Num, b: Num) = balanced(a, O::compareTo, b) as Order
  override fun inc(n: Num) = operated(O::inc, n)
  override fun dec(n: Num) = operated(O::dec, n)
  override fun and(a: Num, b: Num) = balancedBitwise(a, OB::and, b)
  override fun or(a: Num, b: Num) = balancedBitwise(a, OB::or, b)
  override fun xor(a: Num, b: Num) = balancedBitwise(a, OB::xor, b)
  override fun inv(n: Num) = operatedBitwise(OB::inv, n)
  override fun shl(a: Num, k: Cnt) = balancedBitwiseShlShr(a, OB::shl, k)
  override fun shr(a: Num, k: Cnt) = balancedBitwiseShlShr(a, OB::shr, k)
  override fun ushr(a: Num, k: Cnt) = balancedBitwiseShlShr(a, OB::ushr, k)
}

object Balanced : AutoBalancedOps(::balance)
object BalancedLossy : AutoBalancedOps(::balanceLossy)
object BalancedBitwise : AutoBalancedOps(::balanceForBitwise)
