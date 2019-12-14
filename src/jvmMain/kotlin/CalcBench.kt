import jdk.nashorn.api.scripting.NashornScriptEngineFactory
import java.nio.charset.StandardCharsets
import kotlin.random.Random
import kotlin.random.nextInt

private fun Sequence<Char>.joinString() = joinToString("")
private fun <T> Random.pick(items: List<T>): T = items[nextInt(items.indices)]

private fun Random.pick(chars: CharSequence): Char = chars[nextInt(chars.indices)]
private fun randCharSeq(cs: CharSequence, size_range: IntRange): StringBuilder = (1..Random.nextInt(size_range))
  .fold(StringBuilder()) { sb, _ -> sb.append(Random.pick(cs)) }

private const val binary10 = 128/*2^7*/*2*2*2/*2^10*/
object CalcBench: BasicBench<String>() {
  override lateinit var input: String

  private const val numbersNZ = "123456789"
  private const val numbers = "${numbersNZ}0"
  private const val infix = "+-*" // '/' will cause unnecessary arithmetic errors
  private const val tokenSize = 251
  private fun randNum(): Sequence<Char>
    = randCharSeq(numbersNZ, 1..1).append(randCharSeq(numbers, 1..4)).asSequence()
  private fun randSpace(): Sequence<Char>
    = randCharSeq(" ", 1..2).asSequence()

  private fun generateInput() {
    val generated = randomCode()
    var count = 0
    val previewInput = generated.takeWhile { count++ <= 100 || it !in infix }.joinString()
    input = previewInput + "-" + generated.joinString() //one infix is dropped

    val utfSize = input.toByteArray(StandardCharsets.UTF_16).size
    val kbSize = utfSize*(Char.SIZE_BYTES/Byte.SIZE_BYTES) / binary10
    println("Code: ${previewInput}... utf16-size=$kbSize(KB)")
  }
  private fun randomCode(): Sequence<Char> = sequence {
    yieldAll(randNum())
    for (_i in 1..tokenSize) {
      yieldAll(randSpace())
      val infixOp = Random.pick(infix)
      yield(infixOp)
      yieldAll(randSpace())
      val numberSelected = randNum().joinString().takeIf { infixOp !in setOf('/', '!') || it.toInt() != 0 } ?: "123"
      yieldAll(numberSelected.asSequence())
    }
  }

  private fun benchOnce() {
    val rhino = NashornScriptEngineFactory().scriptEngine
    var res: Int = -1; fun pRes() { println("  = $res") }
    do try {
      generateInput()
      lateinit var calc: Calc; fun reCalc() { calc = Calc(input.iterator()) }
      reCalc(); timed("systemStack") { res = calc.eval() }; pRes()
      reCalc(); timed("AdtStack") { res = calc.infixChain() }; pRes()
      timed("Nashorn JS") { res = (rhino.eval(input) as Double).toInt() }; pRes()
      break
    } catch (e: ArithmeticException) { println(e.message ?: "arithmetic"); continue }
    catch (_: StackOverflowError)  { println("stack OOM"); continue }
    while (true)
  }

  @JvmStatic fun main(vararg args: String) {
    if (args.size == 1)
      for (i in 1..args[0].toInt()) benchOnce()
    else
      benchOnce()
    printReports(); summary()
  }
}