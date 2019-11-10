import kotlin.test.Test
import kotlin.test.assertEquals

class DiffAlgorithmTests {
  @Test
  fun simpleDiff() {
    assertDiff("", "", "")
    assertDiff("a", "a", "a")
    assertDiff("ab", "ab", "a,b")
    assertDiff("ab", "", "(a,b)")
    assertDiff("", "ab", "[a,b]")
    assertDiff("ab", "ac", "a (b) [c]")
    assertDiff("abc", "ab", "a,b (c)")
  }
  fun assertDiff(a: String, b: String, res: String) { assertEquals(res, Diff.diff(a, b).toString()) }
}