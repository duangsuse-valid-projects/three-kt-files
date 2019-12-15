import kotlin.test.Test
import kotlin.test.assertEquals

class Base64 {
  @ExperimentalStdlibApi
  @Test
  fun encode() {
    assertEquals("TWFu", CoderBase64.from("Man".encodeToByteArray()).toString())
  }
}