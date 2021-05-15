package app.crescentcash.src.hash

import app.crescentcash.src.manager.UIManager
import org.bouncycastle.util.encoders.Hex
import java.security.MessageDigest

class HashHelper {

    fun getCashAccountCollision(block: String, txHash: String): String {
        val collisionNumber: String
        val concatenatedTest = block.toLowerCase() + txHash.toLowerCase()
        val hashedConcatenated = SHA256_Hex(concatenatedTest)
        val firstFourBytes = hashedConcatenated.substring(0, 8)
        val decimalNotation = firstFourBytes.toLong(radix = 16)
        val reverseDecimalNotation = StringBuilder(decimalNotation.toString() + "").reverse().toString()
        val paddedDecimal = padString(reverseDecimalNotation)
        collisionNumber = paddedDecimal

        return collisionNumber

    }

    fun getCashAccountEmoji(block: String, txHash: String): String {
        val concatenatedTest = block.toLowerCase() + txHash.toLowerCase()
        println(concatenatedTest)
        val hashedConcatenated = SHA256_Hex(concatenatedTest)
        println(hashedConcatenated)
        val lastFourBytes = hashedConcatenated.substring(56, 64)
        val decimalNotation = lastFourBytes.toLong(radix = 16)
        val modulusRemainder = decimalNotation % 100
        val emojiCodeFromArray = UIManager.emojis[modulusRemainder.toInt()]

        return getEmojiByUnicode(emojiCodeFromArray)

    }

    fun getEmojiByUnicode(unicode: Int): String {
        return String(Character.toChars(unicode))
    }

    private fun padString(input: String): String {
        val length = input.length
        val newString = StringBuilder(input)
        if (length < 10) {
            for (x in length..9)
                newString.append("0")
        }

        return newString.toString()
    }

    companion object {

        fun SHA256(value: String): String {
            try {
                val md = MessageDigest.getInstance("SHA-256")
                md.update(value.toByteArray())
                return md.digest().toHexString()
            } catch (ex: Exception) {
                throw RuntimeException(ex)
            }

        }

        private fun SHA256_Hex(value: String): String {
            try {
                val md = MessageDigest.getInstance("SHA-256")
                md.update(Hex.decode(value.toByteArray()))
                return md.digest().toHexString()
            } catch (ex: Exception) {
                throw RuntimeException(ex)
            }

        }

        private fun ByteArray.toHexString(): String {
            return this.joinToString("") {
                java.lang.String.format("%02x", it)
            }
        }
    }

}
