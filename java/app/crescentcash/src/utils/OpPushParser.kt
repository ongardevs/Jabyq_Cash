package app.crescentcash.src.utils

import java.util.*
import java.util.regex.Pattern

class OpPushParser(opReturn: String) {
    val pushData = ArrayList<OpPush>()

    init {
        val delimiter = "OP_PUSH"
        val escape = "\\"
        val escaped = "$escape$delimiter"
        val trimmedOpReturn = opReturn.trim { it <= ' ' }
        if (trimmedOpReturn.startsWith(delimiter)) {
            val splitData = trimmedOpReturn.split("(?<!${Pattern.quote(escape)})${Pattern.quote(delimiter)}".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (splitDatum in splitData) {
                val pushDataTrimmed = splitDatum.trim { it <= ' ' }
                if (pushDataTrimmed != "") {
                    pushData.add(OpPush(pushDataTrimmed.replace(escaped, delimiter)))
                }
            }
        } else {
            pushData.add(OpPush(trimmedOpReturn.replace(escaped, delimiter)))
        }
    }
}
