import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader

class InputStreamCharIterator(private val reader: Reader): CharIterator() {
  constructor(stream: InputStream): this(BufferedReader(InputStreamReader(stream)))
  override fun hasNext(): Boolean = true
  override fun nextChar(): Char = reader.read().toChar()
}

object CalcMain {
  @JvmStatic
  fun main(vararg arg: String) {
    val stdin = InputStreamCharIterator(System.`in`)
    val calc = Calc(stdin)
    println(calc.eval())
  }
}
