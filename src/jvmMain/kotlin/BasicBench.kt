import java.util.TreeSet
import java.util.NavigableSet
import kotlin.math.pow
import kotlin.math.sqrt

typealias Ratio = Double
class NumberUnits(private val units: NavigableSet<NumberUnit>) {
  private fun maximumFit(n: Ratio): NumberUnit = units.floor(ofRatio(n))!!
  private fun ofRatio(n: Ratio): NumberUnit = NumberUnit(n, "(find)")
  fun show(n: Ratio): String = maximumFit(n).show(n)
  data class NumberUnit(val ratio: Ratio, val name: String): Comparable<NumberUnit> {
    /** name~ = '1.0sec', '2.0secs' */
    private inline val hasModifier get() = name.endsWith('~')
    private inline val unmodifiedName get() = if (hasModifier)
      name.subSequence(0..name.lastIndex.dec()) else name
    fun show(num: Ratio): String {
      val converted = num/ratio
      val suffix = if (hasModifier && converted != 1.0) "${unmodifiedName}s" else unmodifiedName
      return "$converted$suffix"
    }
    override fun compareTo(other: NumberUnit): Int = this.ratio.compareTo(other.ratio)
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is NumberUnit) return false
      return ratio == other.ratio
    }
    override fun hashCode(): Int = ratio.hashCode()
  }
}
fun unitsOf(vararg units: Pair<Ratio, String>): NumberUnits
  = NumberUnits(units.mapTo(TreeSet()) { NumberUnits.NumberUnit(it.first, it.second) })

val timeUnits = unitsOf(
  1.0 to "ns",
  1000.0 to "micros",
  1000_000.0 to "ms",
  1000_000_000.0 to "sec~",
  60000_000_000.0 to "min~"
)
typealias NanoSec = Long
interface Duration {
  val launchedAt: NanoSec; val finishedAt: NanoSec
  val duration: NanoSec get() = finishedAt - launchedAt }
abstract class BasicBench<T> {
  abstract var input: T
  protected open val records: MutableList<BenchResult<T>> = mutableListOf()
  private val timeNanos: Long get() = System.nanoTime()

  protected open val briefInputViewport: Int get() = 50
  inner class BenchResult<T>(val name: String, private val input: T, private val launchedFinished: Pair<NanoSec, NanoSec>): Duration {
    override val launchedAt: NanoSec get() = launchedFinished.first
    override val finishedAt: NanoSec get() = launchedFinished.second
    override fun toString(): String {
      val briefView = input.toString().take(briefInputViewport)
      return "#$name($briefView):[$launchedAt, $finishedAt ~ $duration]"
    }
  }

  protected fun timed(name: String, op: () -> Unit) {
    val launched = timeNanos.also { onBenchLaunch(name, it) }
    try { op() } catch (e: Exception) { onBenchFail(name, e) }
    val finished = timeNanos
    BenchResult(name, input, Pair(launched, finished)).also(::onBenchFinish).let(records::add)
  }
  open fun onBenchLaunch(name: String, launched_at: NanoSec): Unit
    = println("Launch $name $launched_at")
  open fun onBenchFinish(result: BenchResult<T>): Unit
    = println("Finish ${result.name} in ${timeUnits.show(result.duration.toDouble())}")
  open fun onBenchFail(name: String, ex: Exception) { throw ex }

  protected fun printReports() { println("== reports =="); records.forEach(::println) }
  protected fun summary() {
    val profileData = records.histogram(BenchResult<*>::name)
    println("== summary ==")
    for ((name, reports) in profileData) {
      println("${name}: ${reports.size} reports")
      val durations = reports.map(BenchResult<*>::duration).map(Long::toDouble)
      println("of: ${durations.joinToString(transform = timeUnits::show)}")
      println("min=${durations.showUnitBy { min()!! }}, " +
        "max=${durations.showUnitBy { max()!! }}, " +
        "mean=${durations.showUnitBy { mean() }}, " +
        "std=${durations.showUnitBy { std() }}")
      val sorted = durations.sorted()
      println("ascending: ${sorted.joinToString(transform = timeUnits::show)}")
      println("25%=${sorted.showUnitBy { percent(0.25) }}, " +
        "50%(median)=${sorted.showUnitBy { percent(0.5) }}, " +
        "75%=${sorted.showUnitBy { percent(0.75) }}")
      println()
    }
  }
  private fun List<Double>.showUnitBy(op: List<Double>.() -> Double): String = this.op().let(timeUnits::show)
}
/** Percentage must be less than `1.0` */
typealias Percentage = Double
private fun <T> List<T>.percent(n: Percentage): T = this[(lastIndex*n).toInt()]
private fun Collection<Double>.mean(): Double = this.sum() / this.size
private fun Iterable<Double>.sumBy(selector: (Double) -> Double): Double = fold(0.0) { a, n -> a + selector(n) }
private fun Collection<Double>.std(): Double {
  val mean = mean()
  return sqrt(sumBy { (it - mean).pow(2) } / size)
}
private fun <T, I> Iterable<T>.histogram(key: (T) -> I): Map<I, List<T>> {
  val hist: MutableMap<I, MutableList<T>> = mutableMapOf()
  forEach { hist.getOrPut(key(it), ::mutableListOf).add(it) }
  return hist
}
