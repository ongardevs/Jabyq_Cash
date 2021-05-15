package app.crescentcash.src.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import app.crescentcash.src.R
import app.crescentcash.src.manager.UIManager
import app.crescentcash.src.manager.WalletManager
import app.crescentcash.src.utils.Constants
import org.bitcoinj.core.Transaction
import org.bitcoinj.utils.MonetaryFormat
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*

class ViewTransactionActivity : AppCompatActivity() {
    private lateinit var txInfoTV: TextView
    private lateinit var btnViewTx: Button
    private var txPosition: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIManager.determineTheme(this)
        this.setContentView(R.layout.view_tx)
        this.findViews()
        this.prepareViews()
        this.initListeners()
    }

    private fun prepareViews() {
        val extras = intent.extras
        txPosition = extras?.getInt(Constants.INTENT_TRANSACTION_POSITION_DATA) ?: 0
        val tx = WalletManager.txList[txPosition!!]
        this.displayTxWindow(tx)
    }

    private fun findViews() {
        txInfoTV = this.findViewById(R.id.txInfoTV)
        btnViewTx = this.findViewById(R.id.btnViewTx)
    }

    private fun initListeners() {

    }

    private fun displayTxWindow(tx: Transaction) {
        val symbols = DecimalFormatSymbols(Locale.US)
        var decimalFormatter: DecimalFormat?
        var receivedValueStr = ""

        when {
            WalletManager.displayUnits == MonetaryFormat.CODE_BTC -> {
                decimalFormatter = DecimalFormat("#,###.########", symbols)
                receivedValueStr = MonetaryFormat.BTC.format(tx.getValue(WalletManager.wallet)).toString()
                receivedValueStr = receivedValueStr.replace(WalletManager.displayUnits + " ", "")
                receivedValueStr = decimalFormatter.format(java.lang.Double.parseDouble(receivedValueStr))
            }
            WalletManager.displayUnits == MonetaryFormat.CODE_MBTC -> {
                decimalFormatter = DecimalFormat("#,###.#####", symbols)
                receivedValueStr = MonetaryFormat.MBTC.format(tx.getValue(WalletManager.wallet)).toString()
                receivedValueStr = receivedValueStr.replace(WalletManager.displayUnits + " ", "")
                receivedValueStr = decimalFormatter.format(java.lang.Double.parseDouble(receivedValueStr))
            }
            WalletManager.displayUnits == MonetaryFormat.CODE_UBTC -> {
                decimalFormatter = DecimalFormat("#,###.##", symbols)
                receivedValueStr = MonetaryFormat.UBTC.format(tx.getValue(WalletManager.wallet)).toString()
                receivedValueStr = receivedValueStr.replace(WalletManager.displayUnits + " ", "")
                receivedValueStr = decimalFormatter.format(java.lang.Double.parseDouble(receivedValueStr))
            }
            WalletManager.displayUnits == "sats" -> {
                decimalFormatter = DecimalFormat("#,###", symbols)
                val amt = java.lang.Double.parseDouble(tx.getValue(WalletManager.wallet).toPlainString())
                val formatted = amt * 100000000

                val formattedStr = decimalFormatter.format(formatted)
                receivedValueStr = formattedStr
            }
        }

        receivedValueStr = receivedValueStr.replace(WalletManager.displayUnits + " ", "")
        receivedValueStr = receivedValueStr.replace("-", "")
        val feeStr: String

        if (tx.fee != null) {
            decimalFormatter = DecimalFormat("#,###.########", symbols)
            var feeValueStr = MonetaryFormat.BTC.format(tx.fee).toString()
            feeValueStr = feeValueStr.replace("BCH ", "")
            val fee = java.lang.Float.parseFloat(feeValueStr)
            feeStr = decimalFormatter.format(fee.toDouble())
        } else {
            feeStr = "n/a"
        }

        val txConfirmations = tx.confidence
        val txConfirms = "" + txConfirmations.depthInBlocks
        val txDate = tx.updateTime.toString() + ""
        val txHash = tx.hashAsString
        txInfoTV.text = Html.fromHtml("<b>" + WalletManager.displayUnits + " Transferred:</b> " + receivedValueStr + "<br> <b>Fee:</b> " + feeStr + "<br> <b>Date:</b> " + txDate + "<br> <b>Confirmations:</b> " + txConfirms)
        btnViewTx.setOnClickListener { v ->
            val url = if (Constants.IS_PRODUCTION) "https://explorer.bitcoin.com/bch/tx/" else "https://explorer.bitcoin.com/tbch/tx/"
            val uri = Uri.parse(url + txHash) // missing 'http://' will cause crashed
            val intent = Intent(Intent.ACTION_VIEW, uri)
            this.startActivity(intent)
        }
    }


    companion object
}
