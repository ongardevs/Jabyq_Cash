package app.crescentcash.src.activity

import SlpTokenAmountFilter
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.ContactsContract
import android.text.TextUtils
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import app.crescentcash.src.R
import app.crescentcash.src.manager.NetManager
import app.crescentcash.src.manager.UIManager
import app.crescentcash.src.manager.WalletManager
import app.crescentcash.src.uri.URIHelper
import app.crescentcash.src.utils.Constants
import app.crescentcash.src.utils.PrefsUtil
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.ncorti.slidetoact.SlideToActView
import com.vdurmont.emoji.EmojiParser
import org.bitcoinj.core.*
import org.bitcoinj.core.slp.*
import org.bitcoinj.protocols.payments.PaymentProtocol
import org.bitcoinj.protocols.payments.PaymentProtocolException
import org.bitcoinj.protocols.payments.PaymentSession
import org.bitcoinj.protocols.payments.slp.SlpPaymentProtocol
import org.bitcoinj.protocols.payments.slp.SlpPaymentSession
import org.bitcoinj.utils.MonetaryFormat
import org.bitcoinj.wallet.SendRequest
import org.bitcoinj.wallet.Wallet
import org.bouncycastle.util.encoders.Hex
import java.lang.IllegalArgumentException
import java.nio.charset.StandardCharsets
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*
import java.util.concurrent.ExecutionException

class SendSLPActivity : AppCompatActivity() {
    private lateinit var setMaxSLP: Button
    lateinit var slpAmount: TextView
    lateinit var slpRecipientAddress: TextView
    private lateinit var slp_qrScan: ImageView
    lateinit var btnSendSLPSlider: SlideToActView
    lateinit var sendTypeSpinner: Spinner
    private lateinit var contactsBtn: ImageButton
    private var slpToken: SlpToken? = null
    private var decimalSpots: String = ""
    val amount: String
        get() = slpAmount.text.toString()
    val recipient: String
        get() = slpRecipientAddress.text.toString().trim { it <= ' ' }
    lateinit var sendType: String
    lateinit var bchToSend: String
    private var receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (Constants.ACTION_CLEAR_SLP_SEND == intent.action) {
                this@SendSLPActivity.clearSend()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIManager.determineTheme(this)
        this.slpToken = if (WalletManager.currentTokenId != "") WalletManager.getSlpKit().getSlpToken(WalletManager.currentTokenId) else null

        if (this.slpToken != null) {
            decimalSpots = Collections.nCopies(this.slpToken!!.decimals, "#").joinToString(separator = "")
        }

        this.setContentView(R.layout.send_slp)
        this.findViews()
        this.prepareViews()
        this.initListeners()
    }

    private fun findViews() {
        btnSendSLPSlider = this.findViewById(R.id.sendSLPBtn)
        btnSendSLPSlider.visibility = View.VISIBLE
        setMaxSLP = this.findViewById(R.id.setMaxSLP)
        sendTypeSpinner = this.findViewById(R.id.sendTypeSlp)
        slpAmount = this.findViewById(R.id.slpAmount)
        slpRecipientAddress = this.findViewById(R.id.slpRecipientAddress)
        slp_qrScan = this.findViewById(R.id.slp_qrScan)
        contactsBtn = this.findViewById(R.id.contactsBtn)
    }

    private fun prepareViews() {
        var decimalCount = 8
        when (WalletManager.displayUnits) {
            MonetaryFormat.CODE_BTC -> {
                decimalCount = 8
            }
            MonetaryFormat.CODE_MBTC -> {
                decimalCount = 5
            }
            MonetaryFormat.CODE_UBTC -> {
                decimalCount = 2
            }
            "sats" -> {
                decimalCount = 0
            }
        }
        this.slpAmount.filters = arrayOf(SlpTokenAmountFilter(slpToken?.decimals ?: decimalCount))

        val titleLabel = this.findViewById<TextView>(R.id.titleLabel2)
        if (this.slpToken == null) {
            titleLabel.text = this.getString(R.string.send)
            this.slpRecipientAddress.hint = this.getString(R.string.receiver)
        } else {
            val extras = intent.extras
            if (extras == null) {
                titleLabel.text = this.getString(R.string.send)
            } else {
                titleLabel.text = "${this.getString(R.string.send)} ${extras.getString(Constants.INTENT_TOKEN_TICKER_DATA)}"
            }
        }

        sendType = PrefsUtil.prefs.getString("sendTypeSlp", WalletManager.displayUnits) as String

        if(this.slpToken != null) {
            this.sendTypeSpinner.visibility = View.INVISIBLE
            this.contactsBtn.visibility = View.GONE
        }
    }

    private fun initListeners() {
        this.setMaxSLP.setOnClickListener {
            slpAmount.text = if (this.slpToken != null) {
                val tokenBalance = WalletManager.getSlpKit().slpBalances[WalletManager.currentTokenPosition - 1].balance
                UIManager.formatBalanceNoUnit(tokenBalance, "#.$decimalSpots")
            } else {
                var bchBalance = java.lang.Double.parseDouble(WalletManager.getSlpWallet().getBalance(Wallet.BalanceType.ESTIMATED).toPlainString())
                var bchBalanceStr = ""

                when (WalletManager.displayUnits) {
                    MonetaryFormat.CODE_BTC -> {
                        bchBalanceStr = UIManager.formatBalanceNoUnit(bchBalance, "#,###.########")
                    }
                    MonetaryFormat.CODE_MBTC -> {
                        bchBalance *= 1000
                        bchBalanceStr = UIManager.formatBalanceNoUnit(bchBalance, "#,###.#####")
                    }
                    MonetaryFormat.CODE_UBTC -> {
                        bchBalance *= 1000000
                        bchBalanceStr = UIManager.formatBalanceNoUnit(bchBalance, "#,###.##")
                    }
                    "sats" -> {
                        bchBalance *= 100000000
                        bchBalanceStr = UIManager.formatBalanceNoUnit(bchBalance, "#,###")
                    }
                }
                bchBalanceStr
            }
        }

        val slideSLPListener: SlideToActView.OnSlideCompleteListener = object : SlideToActView.OnSlideCompleteListener {
            override fun onSlideComplete(view: SlideToActView) {
                if (this@SendSLPActivity.slpToken != null) {
                    if (!slpRecipientAddress.text.toString().contains("#")) {
                        if(slpRecipientAddress.text.toString().startsWith("http")) {
                            this@SendSLPActivity.processSlpBIP70(slpRecipientAddress.text.toString())
                        } else {
                            try {
                                val tokenId = WalletManager.currentTokenId
                                val amt = java.lang.Double.parseDouble(slpAmount.text.toString())
                                val tx = WalletManager.getSlpKit().createSlpTransaction(slpRecipientAddress.text.toString(), tokenId, amt, null)
                                WalletManager.getSlpKit().broadcastSlpTransaction(tx)
                                val txHexBytes = Hex.encode(tx.bitcoinSerialize())
                                val txHex = String(txHexBytes, StandardCharsets.UTF_8)
                                WalletManager.getSlpKit().broadcastSlpTransaction(tx)

                                if (!WalletManager.useTor) {
                                    NetManager.broadcastTransaction(null, txHex, "https://rest.bitcoin.com/v2/rawtransactions/sendRawTransaction")
                                }

                                NetManager.broadcastTransaction(null, txHex, "https://rest.imaginary.cash/v2/rawtransactions/sendRawTransaction")
                                object : Thread() {
                                    override fun run() {
                                        WalletManager.getSlpKit().recalculateSlpUtxos()
                                    }
                                }.start()
                                UIManager.showToastMessage(this@SendSLPActivity, "Sent!")
                                this@SendSLPActivity.finish()
                            } catch (e: InsufficientMoneyException) {
                                this@SendSLPActivity.runOnUiThread { e.message?.let { this@SendSLPActivity.throwSendError(it) } }
                            } catch (e: Wallet.CouldNotAdjustDownwards) {
                                this@SendSLPActivity.throwSendError("Not enough BCH for fee!")
                            } catch (e: Wallet.ExceededMaxTransactionSize) {
                                this@SendSLPActivity.throwSendError("Transaction is too large!")
                            } catch (e: NullPointerException) {
                                this@SendSLPActivity.throwSendError("Cash Account not found.")
                            } catch (e: IllegalArgumentException) {
                                this@SendSLPActivity.runOnUiThread { e.message?.let { this@SendSLPActivity.throwSendError(it) } }
                            } catch (e: AddressFormatException) {
                                this@SendSLPActivity.throwSendError("Invalid address!")
                            } catch (e: Exception) {
                                this@SendSLPActivity.runOnUiThread { e.message?.let { this@SendSLPActivity.throwSendError(it) } }
                            }
                        }
                    } else {
                        this@SendSLPActivity.runOnUiThread { UIManager.showToastMessage(this@SendSLPActivity, "SLP CashAccts are not supported yet.") }
                    }
                } else {
                    this@SendSLPActivity.send()
                }
            }
        }

        this.btnSendSLPSlider.onSlideCompleteListener = slideSLPListener
        this.slp_qrScan.setOnClickListener { UIManager.clickScanQR(this, Constants.REQUEST_CODE_SCAN_PAY_SLP_TO) }

        val items = arrayOf(WalletManager.displayUnits, UIManager.fiat)
        val adapter = ArrayAdapter(this, R.layout.spinner_item, items)
        sendTypeSpinner.adapter = adapter

        when (sendType) {
            WalletManager.displayUnits -> sendTypeSpinner.setSelection(0)
            UIManager.fiat -> sendTypeSpinner.setSelection(1)
        }

        sendTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, v: View?, position: Int, id: Long) {

                when (position) {
                    0 -> sendType = WalletManager.displayUnits
                    1 -> sendType = UIManager.fiat
                }

                PrefsUtil.prefs.edit().putString("sendTypeSlp", sendType).apply()
                println(sendType)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {

            }
        }

        this.contactsBtn.setOnClickListener { showContactSelectionScreen() }
        val filter = IntentFilter()
        filter.addAction(Constants.ACTION_CLEAR_SLP_SEND)
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter)
    }

    private fun send() {
        if (!WalletManager.downloadingSlp) {
            btnSendSLPSlider.isEnabled = false
            val amount = this.amount
            val amtDblToFrmt: Double

            amtDblToFrmt = if (!TextUtils.isEmpty(amount)) {
                java.lang.Double.parseDouble(amount)
            } else {
                0.0
            }

            val formatter = DecimalFormat("#.########", DecimalFormatSymbols(Locale.US))
            val amtDblFrmt = formatter.format(amtDblToFrmt)
            var amtToSend = java.lang.Double.parseDouble(amtDblFrmt)

            if (sendType == "USD" || sendType == "EUR" || sendType == "AUD") {
                object : Thread() {
                    override fun run() {
                        if (sendType == "USD") {
                            val usdToBch = amtToSend / NetManager.price
                            bchToSend = formatter.format(usdToBch)
                            processPayment()
                        }

                        if (sendType == "EUR") {
                            val usdToBch = amtToSend / NetManager.priceEur
                            bchToSend = formatter.format(usdToBch)
                            processPayment()
                        }

                        if (sendType == "AUD") {
                            val usdToBch = amtToSend / NetManager.priceAud
                            bchToSend = formatter.format(usdToBch)
                            processPayment()
                        }
                    }
                }.start()
            } else {
                if (sendType == MonetaryFormat.CODE_BTC) {
                    println("No formatting needed")
                    bchToSend = formatter.format(amtToSend)
                }

                if (sendType == MonetaryFormat.CODE_MBTC) {
                    val mBTCToSend = amtToSend
                    amtToSend = mBTCToSend / 1000.0
                    bchToSend = formatter.format(amtToSend)
                }

                if (sendType == MonetaryFormat.CODE_UBTC) {
                    val uBTCToSend = amtToSend
                    amtToSend = uBTCToSend / 1000000.0
                    bchToSend = formatter.format(amtToSend)
                }

                if (sendType == "sats") {
                    val satsToSend = amtToSend.toLong()
                    amtToSend = satsToSend / 100000000.0
                    bchToSend = formatter.format(amtToSend)
                }

                processPayment()
            }
        } else {
            this.throwSendError("Wallet hasn't finished syncing!")
        }
    }

    private fun processPayment() {
        println(bchToSend)
        if (!TextUtils.isEmpty(amount) && !TextUtils.isEmpty(bchToSend) && slpAmount.text != null && bchToSend != "") {
            val amtCheckVal = java.lang.Double.parseDouble(Coin.parseCoin(bchToSend).toPlainString())

            if (TextUtils.isEmpty(recipient)) {
                throwSendError("Please enter a recipient.")
            } else if (amtCheckVal < 0.00001) {
                throwSendError("Enter a valid amount. Minimum is 0.00001 BCH")
            } else if (WalletManager.getBalance(WalletManager.getSlpWallet()).isLessThan(Coin.parseCoin(amtCheckVal.toString()))) {
                throwSendError("Insufficient balance!")
            } else {
                val recipientText = recipient
                val uri = URIHelper(this, recipientText, false, this.slpToken != null)
                val address = uri.address

                val amount = if (uri.amount != "null")
                    uri.amount
                else
                    "null"

                if (amount != "null")
                    bchToSend = amount

                if (address.startsWith("http")) {
                    this.runOnUiThread {
                        this.slpRecipientAddress.text = address
                        sendTypeSpinner.setSelection(0)
                    }
                    this.sendType = WalletManager.displayUnits
                    PrefsUtil.prefs.edit().putString("sendTypeSlp", this.sendType).apply()

                    this.processBIP70(address)
                } else if (address.startsWith("+")) {
                    val rawNumber = URIHelper().getRawPhoneNumber(address)
                    val numberString = rawNumber.replace("+", "")
                    var amtToSats = java.lang.Double.parseDouble(bchToSend)
                    val satFormatter = DecimalFormat("#", DecimalFormatSymbols(Locale.US))
                    amtToSats *= 100000000
                    val sats = satFormatter.format(amtToSats).toInt()
                    val url = "https://pay.cointext.io/p/$numberString/$sats"
                    println(url)
                    this.processBIP70(url)
                } else {
                    if (address.contains("#")) {
                        val toAddressFixed = EmojiParser.removeAllEmojis(address)
                        val toAddressStripped = toAddressFixed.replace("; ", "")
                        sendCoins(bchToSend, toAddressStripped)
                    } else {
                        try {
                            sendCoins(bchToSend, address)
                        } catch (e: AddressFormatException) {
                            e.printStackTrace()
                            throwSendError("Invalid address!")
                        }
                    }
                }
            }
        } else {
            throwSendError("Please enter an amount.")
        }
    }

    private fun sendCoins(amount: String, toAddress: String) {
        if (toAddress.contains("#") || Address.isValidCashAddr(WalletManager.parameters, toAddress) || Address.isValidLegacyAddress(WalletManager.parameters, toAddress) && (!LegacyAddress.fromBase58(WalletManager.parameters, toAddress).p2sh || WalletManager.allowLegacyP2SH)) {
            object : Thread() {
                override fun run() {
                    val coinAmt = Coin.parseCoin(amount)

                    if (coinAmt.getValue() > 0.0) {
                        try {
                            val req: SendRequest

                            if (WalletManager.useTor) {
                                if (coinAmt == WalletManager.getBalance(WalletManager.getSlpWallet())) {
                                    req = SendRequest.emptyWallet(WalletManager.parameters, toAddress, NetManager.torProxy)
                                    /*
                                        Bitcoincashj requires emptying the wallet to only have a single output for some reason.
                                        So, we cache the original setting above, then set the real setting to false here.

                                        After doing the if(addOpReturn) check below, we restore it to its actual setting.
                                    */
                                } else {
                                    req = SendRequest.to(WalletManager.parameters, toAddress, coinAmt, NetManager.torProxy)
                                }
                            } else {
                                if (coinAmt == WalletManager.getBalance(WalletManager.getSlpWallet())) {
                                    req = SendRequest.emptyWallet(WalletManager.parameters, toAddress)
                                    /*
                                        Bitcoincashj requires emptying the wallet to only have a single output for some reason.
                                        So, we cache the original setting above, then set the real setting to false here.

                                        After doing the if(addOpReturn) check below, we restore it to its actual setting.
                                    */
                                } else {
                                    req = SendRequest.to(WalletManager.parameters, toAddress, coinAmt)
                                }

                            }

                            req.allowUnconfirmed()
                            req.ensureMinRequiredFee = false
                            req.feePerKb = Coin.valueOf(2L * 1000L)

                            val tx = WalletManager.getSlpWallet().sendCoinsOffline(req)
                            val txHexBytes = Hex.encode(tx.bitcoinSerialize())
                            val txHex = String(txHexBytes, StandardCharsets.UTF_8)
                            WalletManager.slpWalletKit!!.broadcastSlpTransaction(tx)

                            if (!WalletManager.useTor) {
                                NetManager.broadcastTransaction(null, txHex, "https://rest.bitcoin.com/v2/rawtransactions/sendRawTransaction")
                            }

                            NetManager.broadcastTransaction(null, txHex, "https://rest.imaginary.cash/v2/rawtransactions/sendRawTransaction")
                        } catch (e: InsufficientMoneyException) {
                            e.printStackTrace()
                            this@SendSLPActivity.runOnUiThread { e.message?.let { UIManager.showToastMessage(this@SendSLPActivity, it) } }
                        } catch (e: Wallet.CouldNotAdjustDownwards) {
                            e.printStackTrace()
                            throwSendError("Not enough BCH for fee!")
                        } catch (e: Wallet.ExceededMaxTransactionSize) {
                            e.printStackTrace()
                            throwSendError("Transaction is too large!")
                        } catch (e: NullPointerException) {
                            e.printStackTrace()
                            throwSendError("Cash Account not found.")
                        }

                    }
                }
            }.start()
        } else if (!Address.isValidCashAddr(WalletManager.parameters, toAddress) || !Address.isValidLegacyAddress(WalletManager.parameters, toAddress)) {
            throwSendError("Invalid address!")
        }
    }

    private fun processBIP70(url: String) {
        try {
            val future: ListenableFuture<PaymentSession> = PaymentSession.createFromUrl(url)

            val session = future.get()
            if (session.isExpired) {
                throwSendError("Invoice expired!")
            }

            val req = session.sendRequest
            req.allowUnconfirmed()
            WalletManager.getSlpWallet().completeTx(req)

            val ack = session.sendPayment(ImmutableList.of(req.tx), WalletManager.getSlpWallet().freshReceiveAddress(), null)
            if (ack != null) {
                Futures.addCallback<PaymentProtocol.Ack>(ack, object : FutureCallback<PaymentProtocol.Ack> {
                    override fun onSuccess(ack: PaymentProtocol.Ack?) {
                        WalletManager.getSlpWallet().commitTx(req.tx)
                        this@SendSLPActivity.clearSend()
                    }

                    override fun onFailure(throwable: Throwable) {
                        throwSendError("An error occurred.")
                    }
                }, MoreExecutors.directExecutor())
            }
        } catch (e: InsufficientMoneyException) {
            throwSendError("You do not have enough BCH!")
        }
    }

    private fun showContactSelectionScreen() {
        val pickContact = Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
        pickContact.type = ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE
        this.startActivityForResult(pickContact, Constants.REQUEST_CODE_GET_SLP_CONTACT)
    }

    fun throwSendError(message: String) {
        this.runOnUiThread {
            UIManager.showToastMessage(this, message)
            this.setSendButtonsActive()
        }
    }

    fun setSendButtonsActive() {
        this.runOnUiThread {
            this.btnSendSLPSlider.isEnabled = true
            this.btnSendSLPSlider.resetSlider()
        }
    }

    fun clearSend() {
        this.runOnUiThread {
            this.btnSendSLPSlider.isEnabled = true
            this.btnSendSLPSlider.resetSlider()
            this.slpRecipientAddress.text = null
            this.slpAmount.text = null
        }
    }

    fun processScanOrPaste(text: String) {
        val uri = URIHelper(this, text, true, this.slpToken != null)
        val address = uri.address

        if (address.startsWith("http")) {
            this.runOnUiThread {
                this.slpRecipientAddress.text = address
                this.sendTypeSpinner.setSelection(0)
            }

            sendType = WalletManager.displayUnits
            PrefsUtil.prefs.edit().putString("sendTypeSlp", WalletManager.sendType).apply()

            if(text.startsWith("simpleledger")) {
                if(this.slpToken != null) {
                    WalletManager.getSlpBIP70Data(this, address)
                }
            } else {
                WalletManager.getBIP70Data(this, address)
            }
        }
    }

    private fun processSlpBIP70(url: String) {
        try {
            val future: ListenableFuture<SlpPaymentSession> = SlpPaymentSession.createFromUrl(url)

            val session = future.get()
            if (session.isExpired) {
                throwSendError("Invoice expired!")
                return
            }

            val tokenId = session.tokenId
            val slpToken = WalletManager.slpWalletKit?.getSlpToken(tokenId)
            if(slpToken != null) {
                val rawTokens = session.rawTokenAmounts
                val addresses = session.getSlpAddresses(WalletManager.parameters)
                val tx = WalletManager.slpWalletKit?.createSlpTransactionBip70(tokenId, null, rawTokens, addresses, session)
                val ack = session.sendPayment(ImmutableList.of(tx!!), WalletManager.slpWalletKit?.wallet?.freshReceiveAddress(), null)
                if (ack != null) {
                    Futures.addCallback<SlpPaymentProtocol.Ack>(ack, object : FutureCallback<SlpPaymentProtocol.Ack> {
                        override fun onSuccess(ack: SlpPaymentProtocol.Ack?) {
                            this@SendSLPActivity.clearSend()
                        }

                        override fun onFailure(throwable: Throwable) {
                            throwSendError("An error occurred.")
                        }
                    }, MoreExecutors.directExecutor())
                }
            } else {
                this@SendSLPActivity.throwSendError("Unknown token!")
            }
        } catch (e: InsufficientMoneyException) {
            this@SendSLPActivity.runOnUiThread { e.message?.let { this@SendSLPActivity.throwSendError(it) } }
        } catch (e: Wallet.CouldNotAdjustDownwards) {
            this@SendSLPActivity.throwSendError("Not enough BCH for fee!")
        } catch (e: Wallet.ExceededMaxTransactionSize) {
            this@SendSLPActivity.throwSendError("Transaction is too large!")
        } catch (e: IllegalArgumentException) {
            this@SendSLPActivity.runOnUiThread { e.message?.let { this@SendSLPActivity.throwSendError(it) } }
        } catch (e: AddressFormatException) {
            this@SendSLPActivity.throwSendError("Invalid address!")
        } catch (e: Exception) {
            this@SendSLPActivity.runOnUiThread { e.message?.let { this@SendSLPActivity.throwSendError(it) } }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(resultCode == Activity.RESULT_OK) {
            if (requestCode != Constants.REQUEST_CODE_GET_SLP_CONTACT) {
                if (data != null) {
                    val scanData = data.getStringExtra(Constants.QR_SCAN_RESULT)
                    if (scanData != null) {
                        if (requestCode == Constants.REQUEST_CODE_SCAN_PAY_SLP_TO) {
                            this.processScanOrPaste(scanData)
                        }
                    }
                }
            } else {
                if (data != null) {
                    if (data.data != null) {
                        val c = contentResolver.query(data.data!!, null, null, null, null)
                        c!!.moveToFirst()
                        try {
                            val id = c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                            val c2 = contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, ContactsContract.CommonDataKinds.Phone.CONTACT_ID +" = "+ id, null, null)
                            c2!!.moveToFirst()
                            val phoneIndex = c2.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER)
                            val num = c2.getString(phoneIndex)
                            this.slpRecipientAddress.text = num
                        } catch (e: Exception) {
                            UIManager.showToastMessage(this, "No phone number found.")
                        }
                        c.close()
                    } else {
                        UIManager.showToastMessage(this, "No contact selected.")
                    }
                } else {
                    UIManager.showToastMessage(this, "No contact selected.")
                }
            }
        }
    }
}
