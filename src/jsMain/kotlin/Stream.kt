actual interface PeekStream<out E> {
  actual val peek: E get
  actual fun consume(): E
}
