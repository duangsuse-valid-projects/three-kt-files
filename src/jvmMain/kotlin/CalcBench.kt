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
  private fun generateInput() {
    val generated = randomCode()
    val previewInput = generated.take(100).joinString()
    input = previewInput + generated.joinString()

    val utfSize = input.toByteArray(StandardCharsets.UTF_16).size
    val kbSize = utfSize*2/1024
    println("Code: ${previewInput}... utf16-size=$kbSize(KB)")
  }

  private fun benchOnce() {
    //var finishing = 0; fun doneOne() { ++finishing }
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
      break
    } catch (_: ArithmeticException) { continue }
    catch (_: StackOverflowError)  { continue }
    while (true)
  }

  @JvmStatic fun main(vararg args: String) {
    if (args.size == 1)
      for (i in 1..args[0].toInt()) benchOnce()
    else
      benchOnce()
    printReports(); summary()
  }

  private fun randomCode(): Sequence<Char> = sequence {
    val numbers = "0123456789"
    val infix = "+-*/!"
    fun randNum() = randCharSeq(numbers, 2..5).asSequence()
    fun randSpace() = randCharSeq(" ", 1..2).asSequence()
    yieldAll(randNum())
    for (_i in 1..5_000) {
      yieldAll(randSpace())
      val infixOp = Random.pick(infix)
      yield(infixOp)
      yieldAll(randSpace())
      val numberSelected = randNum().joinString().takeIf { infixOp !in setOf('/', '!') || it.toInt() != 0 } ?: "123"
      yieldAll(numberSelected.asSequence())
    }
  }
}