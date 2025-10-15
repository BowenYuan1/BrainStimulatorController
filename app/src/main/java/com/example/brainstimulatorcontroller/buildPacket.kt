private const val START_BYTE: UByte = 0xAAu
private const val END_BYTE: UByte   = 0x55u

/**
 * Build packet: [AA][cmd][p0][p1][p2][p3][chk][55]
 * - payload is a 32-bit int, LITTLE-ENDIAN here.
 * - checksum = (cmd + p0 + p1 + p2 + p3) & 0xFF
 */
private fun buildPacket(cmd: UByte, payload: Int): ByteArray {
    val bytes = ByteArray(8)
    var i = 0
    bytes[i++] = START_BYTE.toByte()
    bytes[i++] = cmd.toByte()

    // payload little-endian
    val p0 =  (payload        and 0xFF).toByte()
    val p1 = ((payload shr 8)  and 0xFF).toByte()
    val p2 = ((payload shr 16) and 0xFF).toByte()
    val p3 = ((payload shr 24) and 0xFF).toByte()
    bytes[i++] = p0
    bytes[i++] = p1
    bytes[i++] = p2
    bytes[i++] = p3

    val chk = ((cmd.toInt() + (p0.toInt() and 0xFF) + (p1.toInt() and 0xFF) +
            (p2.toInt() and 0xFF) + (p3.toInt() and 0xFF)) and 0xFF).toByte()
    bytes[i++] = chk
    bytes[i]   = END_BYTE.toByte()
    return bytes
}
