actual interface PeekStream<out E> {
  actual val peek: E get
  @Throws(StreamEnd::class)
  actual fun consume(): E
}