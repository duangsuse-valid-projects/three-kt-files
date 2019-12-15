import kotlin.test.Test

class RegcxTests {
  @Test
  fun lexer() {
    val lex = Lexer.make(::分词, "  ^   []()")
    println(lex.tokens().joinToString())
  }
}