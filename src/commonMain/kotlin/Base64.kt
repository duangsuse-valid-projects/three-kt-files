import kotlin.experimental.and

/** Binary `2**8` 0-63  */
enum class Radix64 {
  A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,V,W,X,Y,Z,
  a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q,r,s,t,u,v,w,x,y,z,
  `0`, `1`, `2`, `3`, `4`, `5`, `6`, `7`, `8`, `9`,
  `+`, `~`, `=`; /*'/'*/
  fun toChar(): Char = if (this == `~`) '/' else this.name[0]
  companion object Read {
    fun from(ordinal: Int): Radix64 = values()[ordinal]
    fun from(c: Char): Radix64 = when (c) {
      in 'A'..'Z' -> from(c.minus('A').toInt())
      in 'a'..'z' -> from(c.minus('a').toInt()+26) //size[A-Z]
      in '0'..'9' -> from(c.minus('0').toInt()+26+26) //size[A-Za-z]
      else -> when (c) { '+' -> `+`; '/' -> `~`; else -> throw Error() }
    } }
}

data class Base64(val radix64s: Array<out Radix64>) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || this::class != other::class) return false
    other as Base64
    if (!radix64s.contentEquals(other.radix64s)) return false
    return true
  }
  override fun hashCode(): Int = radix64s.contentHashCode()
  override fun toString(): String = radix64s.joinToString("") { it.toChar().toString() }
  val size: Int get() = radix64s.size
  operator fun get(index: Int): Radix64 = radix64s[index]
  fun decodeSize(): Int = when {
    this[size.dec().dec()] == Radix64.`=`
      && this[size.dec()] == Radix64.`=` -> size-3 + 1
    this[size.dec()] == Radix64.`=` -> size-3 + 2
    else -> size
  }
}

interface Base64Coder {
  fun from(orig: ByteArray): Base64
  fun to(code: Base64): ByteArray
}

object CoderBase64: Base64Coder {
  private fun fromLen(orig: ByteArray): Int = orig.size.plus(3.dec()) * 3 / 4
  private const val select2 = 0b11.toByte()
  private const val select4 = 0b1111.toByte()
  private const val select6 = 0b1111_1100
  override fun from(orig: ByteArray): Base64 {
    val buffer = Array(fromLen(orig)) { Radix64.`=` }
    val byte0 = orig[0]
    val rest = orig.sliceArray(1..orig.lastIndex)
    var bits6 = byte0.unsignedExt().and(select6) ushr 2
    var lastBits = byte0.and(select2)
    var lastBits2 = true
    for ((pos, byte) in rest.withIndex()) {
      buffer[pos] = Radix64.from(bits6)
      val patchBits = if (lastBits2) 2 else 4
      val apart = if (lastBits2) 4 else 2
      bits6 = lastBits.toInt().shl(apart).or(byte.unsignedExt() ushr apart)
      lastBits2 = !lastBits2
      val select = if (lastBits2) select2 else select4
      lastBits = byte.and(select)
    }
    return Base64(buffer)
  }
  override fun to(code: Base64): ByteArray { TODO() }
  private fun Byte.unsignedExt(): Int = if (this < 0) 0b1000_0000 or this.toInt() else this.toInt()
}
