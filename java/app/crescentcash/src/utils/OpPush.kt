package app.crescentcash.src.utils

import org.bouncycastle.util.encoders.Hex

class OpPush(val data: String) {

    private val isHex: Boolean
        get() {
            return try {
                Hex.decode(this.data)
                true
            } catch (e: Exception) {
                false
            }

        }

    val binaryData: ByteArray
        get() = if (this.isHex) {
            Hex.decode(this.data)
        } else {
            this.data.toByteArray()
        }
}
