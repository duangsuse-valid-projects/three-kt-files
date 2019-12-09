import jdk.nashorn.api.scripting.NashornScriptEngineFactory
import java.nio.charset.StandardCharsets
import kotlin.random.Random
import kotlin.random.nextInt

private fun Sequence<Char>.joinString() = joinToString("")
private fun <T> Random.pick(items: List<T>): T = items[nextInt(items.indices)]

private fun Random.pick(chars: CharSequence): Char = chars[nextInt(chars.indices)]
private fun randCharSeq(cs: CharSequence, size_range: IntRange): StringBuilder = (1..Random.nextInt(size_range))
  .fold(StringBuilder()) { sb, _ -> sb.append(Random.pick(cs)) }

object CalcBench: BasicBench<String>() {
  override lateinit var input: String

  private const val numbersNZ = "123456789"
  private const val numbers = "${numbersNZ}0"
  private const val infix = "+-*/"
  private const val tokenSize = 10
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
    val kbSize = utfSize*2/1024
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
    do try {
      generateInput()
      timed("systemStack") {
        val calc = Calc(input.iterator())
        val res = calc.eval()
        println("  = $res")
      }
      timed("AdtStack") {
        val calc = Calc(input.iterator())
        val res = calc.infixChain()
        println("  = $res")
      }
      timed("Nashorn JS") {
        val rhino = NashornScriptEngineFactory().scriptEngine
        val res = rhino.eval(input)
        println("  = $res")
      }
      break
    } catch (e: ArithmeticException) { print(e.message); continue }
    catch (_: StackOverflowError)  { print("stack OOM"); continue }
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