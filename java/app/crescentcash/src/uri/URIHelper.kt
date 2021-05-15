package app.crescentcash.src.uri

import app.crescentcash.src.activity.SendActivity
import app.crescentcash.src.activity.SendSLPActivity
import app.crescentcash.src.manager.WalletManager
import app.crescentcash.src.utils.PrefsUtil
import kotlinx.android.synthetic.main.send.*
import org.bitcoinj.utils.MonetaryFormat
import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.text.DecimalFormat
import java.util.*

class URIHelper() {
    lateinit var address: String
    lateinit var amount: String

    constructor(sendActivity: SendActivity?, uri: String, tryForSend: Boolean) : this() {
        this.init(sendActivity, uri, tryForSend)
    }

    constructor(uri: String, tryForSend: Boolean) : this() {
        this.init(null, uri, tryForSend)
    }

    constructor(sendSlpActivity: SendSLPActivity?, uri: String, tryForSend: Boolean, sendingToken: Boolean) : this() {
        this.init(sendSlpActivity, uri, tryForSend, sendingToken)
    }

    fun processSendAmount(amount: String): String {
        return when (WalletManager.sendType) {
            MonetaryFormat.CODE_BTC -> {
                convertBchToDenom(amount, 1.0)
            }
            MonetaryFormat.CODE_MBTC -> {
                convertBchToDenom(amount, 1000.0)
            }
            MonetaryFormat.CODE_UBTC -> {
                convertBchToDenom(amount, 1000000.0)
            }
            "sats" -> {
                convertBchToDenom(amount, 100000000.0)
            }
            else -> convertBchToDenom(amount, 1.0)
        }
    }

    private fun convertBchToDenom(bchAmount: String, modifier: Double): String {
        val df = DecimalFormat("#,###.########")
        var amt = java.lang.Double.parseDouble(bchAmount)
        amt *= modifier
        return df.format(amt).replace(",", "")
    }

    private fun getQueryParams(url: String): Map<String, List<String>> {
        try {
            val params = HashMap<String, List<String>>()
            val urlParts = url.split("\\?".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (urlParts.size > 1) {
                val query = urlParts[1]
                for (param in query.split("&".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
                    val pair = param.split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    val key = URLDecoder.decode(pair[0], "UTF-8")
                    var value = ""
                    if (pair.size > 1) {
                        value = URLDecoder.decode(pair[1], "UTF-8")
                    }

                    var values: MutableList<String>? = params[key] as MutableList<String>?
                    if (values == null) {
                        values = ArrayList()
                        params[key] = values
                    }
                    values.add(value)
                }
            }

            return params
        } catch (ex: UnsupportedEncodingException) {
            throw AssertionError(ex)
        }
    }

    fun getRawPhoneNumber(address: String): String {
        val cointextString = address.replace("cointext:", "")
        val removedDashes = cointextString.replace("-", "")
        val removedOpenParenthesis = removedDashes.replace("(", "")
        val removedClosedParenthesis = removedOpenParenthesis.replace(")", "")
        var number = removedClosedParenthesis.replace(".", "")

        if (!number.contains("+")) {
            number = "+1$number"
        }

        return number
    }

    private fun getQueryBaseAddress(url: String): String {
        val urlParts = url.split("\\?".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        return if (urlParts.size > 1) {
            urlParts[0]
        } else {
            url
        }
    }

    private fun init(sendActivity: SendActivity?, uri: String, tryForSend: Boolean) {
        val mappedVariables = getQueryParams(uri)

        if (!uri.contains("http")) {
            if (mappedVariables["amount"] != null) {
                val amountVariable = (mappedVariables["amount"] ?: error(""))[0]

                amount = processSendAmount(amountVariable)

                sendActivity?.runOnUiThread {
                    sendActivity.amountText.text = amount
                    sendActivity.sendTypeSpinner.setSelection(0)
                }

                WalletManager.sendType = WalletManager.displayUnits
                PrefsUtil.prefs.edit().putString("sendType", WalletManager.sendType).apply()
            } else {
                amount = "null"
            }

            address = when {
                uri.startsWith(WalletManager.parameters.cashAddrPrefix) -> getQueryBaseAddress(uri).replace(WalletManager.parameters.cashAddrPrefix + ":", "")
                uri.startsWith("cointext") -> this.getRawPhoneNumber(getQueryBaseAddress(uri))
                uri.startsWith("cashacct") -> getQueryBaseAddress(uri).replace("cashacct:", "")
                uri.startsWith("simpleledger") -> getQueryBaseAddress(uri).replace("simpleledger:", "")
                else -> getQueryBaseAddress(uri)
            }

            sendActivity?.runOnUiThread { sendActivity.displayRecipientAddress(address) }

            if (tryForSend) {
                if (mappedVariables["amount"] != null && this.amount != "null") {
                    val amountAsFloat = amount.toFloat()
                    println("Amount scanned: $amountAsFloat Maximum amount set: ${WalletManager.maximumAutomaticSend}")
                    if (amountAsFloat <= WalletManager.maximumAutomaticSend) {
                        sendActivity?.btnSendSlider?.completeSlider()
                    }
                }
            }
        } else {
            address = if (mappedVariables["r"] != null) {
                (mappedVariables["r"] ?: error(""))[0]
            } else {
                uri
            }

            sendActivity?.runOnUiThread { sendActivity.sendTypeSpinner.setSelection(0) }
            WalletManager.sendType = WalletManager.displayUnits
            PrefsUtil.prefs.edit().putString("sendType", WalletManager.sendType).apply()
            sendActivity?.runOnUiThread { sendActivity.displayRecipientAddress(address) }
            amount = "null"
        }
    }

    private fun init(sendActivity: SendSLPActivity?, uri: String, tryForSend: Boolean, sendingToken: Boolean) {
        val mappedVariables = getQueryParams(uri)

        if (!uri.contains("http")) {
            if (mappedVariables["amount"] != null) {
                val amountVariable = (mappedVariables["amount"] ?: error(""))[0]

                amount = if(sendingToken) {
                    amountVariable
                } else {
                    processSendAmount(amountVariable)
                }

                sendActivity?.runOnUiThread {
                    sendActivity.slpAmount.text = amount
                    sendActivity.sendTypeSpinner.setSelection(0)
                }

                sendActivity?.sendType = WalletManager.displayUnits
                PrefsUtil.prefs.edit().putString("sendTypeSlp", sendActivity?.sendType).apply()
            } else {
                amount = "null"
            }

            address = when {
                uri.startsWith(WalletManager.parameters.cashAddrPrefix) -> getQueryBaseAddress(uri).replace(WalletManager.parameters.cashAddrPrefix + ":", "")
                uri.startsWith("cointext") -> this.getRawPhoneNumber(getQueryBaseAddress(uri))
                uri.startsWith("cashacct") -> getQueryBaseAddress(uri).replace("cashacct:", "")
                uri.startsWith("simpleledger") -> getQueryBaseAddress(uri).replace("simpleledger:", "")
                else -> getQueryBaseAddress(uri)
            }

            sendActivity?.runOnUiThread { sendActivity.slpRecipientAddress.text = address }

            if (tryForSend && !sendingToken) {
                if (mappedVariables["amount"] != null && this.amount != "null") {
                    val amountAsFloat = amount.toFloat()
                    println("Amount scanned: $amountAsFloat Maximum amount set: ${WalletManager.maximumAutomaticSend}")
                    if (amountAsFloat <= WalletManager.maximumAutomaticSend) {
                        sendActivity?.btnSendSLPSlider?.completeSlider()
                    }
                }
            }
        } else {
            address = if (mappedVariables["r"] != null) {
                (mappedVariables["r"] ?: error(""))[0]
            } else {
                uri
            }

            sendActivity?.runOnUiThread { sendActivity.sendTypeSpinner.setSelection(0) }
            sendActivity?.sendType = WalletManager.displayUnits
            PrefsUtil.prefs.edit().putString("sendTypeSlp", sendActivity?.sendType).apply()
            sendActivity?.runOnUiThread { sendActivity.slpRecipientAddress.text = address }
            amount = "null"
        }
    }
}