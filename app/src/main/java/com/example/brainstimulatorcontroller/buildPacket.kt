package com.example.brainstimulatorcontroller
private const val START_BYTE: UByte = 0xAAu
private const val END_BYTE: UByte   = 0x55u
private const val PAYLOAD_LEN_BYTES = 8  // 64-bit payload

/**
 * Wire format: [AA][HEADER][p0..p7][chk][55]
 * HEADER = (cmd & 0xF) << 4 | (channel & 0xF)
 * checksum = (HEADER + sum(payload bytes)) & 0xFF
 */
fun buildPacket(cmd: UByte, phase: Int, payload: ByteArray): ByteArray {
    require(payload.size == PAYLOAD_LEN_BYTES) { "Payload must be $PAYLOAD_LEN_BYTES bytes" }

    val header = (((cmd.toInt() and 0xF) shl 4) or (phase and 0xF)) and 0xFF
    val out = ByteArray(1 + PAYLOAD_LEN_BYTES)
    out[0] = header.toByte()
    System.arraycopy(payload, 0, out, 1, PAYLOAD_LEN_BYTES)
    return out
}
