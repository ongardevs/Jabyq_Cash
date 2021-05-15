package app.crescentcash.src.activity

import android.app.Activity
import android.content.*
import android.os.Bundle
import android.provider.ContactsContract
import android.text.TextUtils
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import app.crescentcash.src.R
import app.crescentcash.src.listener.RecipientTextListener
import app.crescentcash.src.manager.UIManager
import app.crescentcash.src.manager.WalletManager
import app.crescentcash.src.ui.RecipientEditText
import app.crescentcash.src.uri.URIHelper
import app.crescentcash.src.utils.Constants
import app.crescentcash.src.utils.PrefsUtil
import com.ncorti.slidetoact.SlideToActView
import org.bitcoinj.utils.MonetaryFormat

class SendActivity : AppCompatActivity() {
    private lateinit var tvRecipientAddress_AM: RecipientEditText
    lateinit var amountText: TextView
    lateinit var btnSendSlider: SlideToActView
    lateinit var setMaxCoins: Button
    lateinit var sendTypeSpinner: Spinner
    private lateinit var donateBtn: TextView
    private lateinit var contactsBtn: ImageButton
    lateinit var opReturnText: EditText
    lateinit var opReturnBox: LinearLayout
    private lateinit var qrScan: ImageView
    private var receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (Constants.ACTION_CLEAR_SEND == intent.action) {
                this@SendActivity.clearSend()
            }
        }
    }
    val recipient: String
        get() = tvRecipientAddress_AM.text.toString().trim { it <= ' ' }

    val amount: String
        get() = amountText.text.toString()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIManager.determineTheme(this)
        this.setContentView(R.layout.send)
        this.findViews()
        this.prepareViews()
        this.initListeners()
    }

    private fun findViews() {
        tvRecipientAddress_AM = this.findViewById(R.id.tvRecipientAddress_AM)
        amountText = this.findViewById(R.id.etAmount_AM)
        setMaxCoins = this.findViewById(R.id.setMaxCoins)
        sendTypeSpinner = this.findViewById(R.id.sendType)
        donateBtn = this.findViewById(R.id.donateBtn)
        contactsBtn = this.findViewById(R.id.contactsBtn)
        opReturnText = this.findViewById(R.id.opReturnText)
        opReturnBox = this.findViewById(R.id.opReturnBox)
        qrScan = this.findViewById(R.id.qrScan)
        btnSendSlider = this.findViewById(R.id.btnSendSlider)
        btnSendSlider.visibility = View.VISIBLE
    }

    private fun prepareViews() {
        val items = arrayOf(WalletManager.displayUnits, UIManager.fiat)
        val adapter = ArrayAdapter(this, R.layout.spinner_item, items)
        sendTypeSpinner.adapter = adapter

        when (WalletManager.sendType) {
            WalletManager.displayUnits -> sendTypeSpinner.setSelection(0)
            UIManager.fiat -> sendTypeSpinner.setSelection(1)
        }

        sendTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, v: View?, position: Int, id: Long) {

                when (position) {
                    0 -> WalletManager.sendType = WalletManager.displayUnits
                    1 -> WalletManager.sendType = UIManager.fiat
                }

                PrefsUtil.prefs.edit().putString("sendType", WalletManager.sendType).apply()
                println(WalletManager.sendType)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {

            }
        }

        opReturnBox.visibility = if (WalletManager.addOpReturn) {
            View.VISIBLE
        } else {
            View.GONE
        }

        if (UIManager.streetModeEnabled) {
            this.setMaxCoins.isEnabled = false
            this.setMaxCoins.backgroundTintList = ContextCompat.getColorStateList(this, R.color.gray)
        }
    }

    private fun initListeners() {
        val slideBCHListener: SlideToActView.OnSlideCompleteListener = object : SlideToActView.OnSlideCompleteListener {
            override fun onSlideComplete(view: SlideToActView) {
                WalletManager.send(this@SendActivity)
            }
        }
        this.btnSendSlider.onSlideCompleteListener = slideBCHListener
        val listener: RecipientTextListener = object : RecipientTextListener {
            override fun onUpdate() {
                val clipboard = this@SendActivity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipboardText = clipboard.text.toString()

                processScanOrPaste(clipboardText)
            }
        }
        this.setMaxCoins.setOnClickListener { setMaxCoins() }
        this.contactsBtn.setOnClickListener { showContactSelectionScreen() }
        this.tvRecipientAddress_AM.addListener(listener)
        this.donateBtn.setOnClickListener { this.displayRecipientAddress(Constants.DONATION_ADDRESS) }
        this.qrScan.setOnClickListener { UIManager.clickScanQR(this, Constants.REQUEST_CODE_SCAN_PAY_TO) }
        val filter = IntentFilter()
        filter.addAction(Constants.ACTION_CLEAR_SEND)
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter)
    }

    private fun setMaxCoins() {
        WalletManager.sendType = WalletManager.displayUnits
        sendTypeSpinner.setSelection(0)
        PrefsUtil.prefs.edit().putString("sendType", WalletManager.sendType).apply()
        var balBch: Double = if (WalletManager.selectedUtxos.size == 0) {
            java.lang.Double.parseDouble(WalletManager.getBalance(WalletManager.wallet).toPlainString())
        } else {
            java.lang.Double.parseDouble(WalletManager.getMaxValueOfSelectedUtxos().toPlainString())
        }

        val coins = when (WalletManager.displayUnits) {
            MonetaryFormat.CODE_BTC -> {
                UIManager.formatBalanceNoUnit(balBch, "#.########")
            }
            MonetaryFormat.CODE_MBTC -> {
                balBch *= 1000
                UIManager.formatBalanceNoUnit(balBch, "#.#####")
            }
            MonetaryFormat.CODE_UBTC -> {
                balBch *= 1000000
                UIManager.formatBalanceNoUnit(balBch, "#.##")
            }
            "sats" -> {
                balBch *= 100000000
                UIManager.formatBalanceNoUnit(balBch, "#")
            }
            else -> UIManager.formatBalanceNoUnit(balBch, "#.########")
        }

        println("Setting...")
        this.runOnUiThread { amountText.text = coins }
    }

    private fun showContactSelectionScreen() {
        val pickContact = Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
        pickContact.type = ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE
        this.startActivityForResult(pickContact, Constants.REQUEST_CODE_GET_CONTACT)
    }

    fun displayRecipientAddress(recipientAddress: String?) {
        if (recipientAddress != null) {
            if (TextUtils.isEmpty(recipientAddress)) {
                tvRecipientAddress_AM.hint = this.resources.getString(R.string.receiver)
            } else {
                tvRecipientAddress_AM.setText(recipientAddress)
            }
        } else {
            tvRecipientAddress_AM.text = null
            tvRecipientAddress_AM.hint = this.resources.getString(R.string.receiver)
        }
    }

    fun clearSend() {
        this.setSendButtonsActive()
        this.displayRecipientAddress(null)
        this.clearAmount()
        this.opReturnText.text = null
        WalletManager.selectedUtxos = ArrayList()
    }

    fun setSendButtonsActive() {
        this.runOnUiThread {
            this.btnSendSlider.isEnabled = true
            this.btnSendSlider.resetSlider()
        }
    }

    private fun clearAmount() {
        amountText.text = null
    }

    fun processScanOrPaste(text: String) {
        val uri = URIHelper(this, text, true)
        val address = uri.address

        if (address.startsWith("http")) {
            this.runOnUiThread {
                this.displayRecipientAddress(address)
                this.sendTypeSpinner.setSelection(0)
            }

            WalletManager.sendType = WalletManager.displayUnits
            PrefsUtil.prefs.edit().putString("sendType", WalletManager.sendType).apply()

            WalletManager.getBIP70Data(this, address)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(resultCode == Activity.RESULT_OK) {
            if (requestCode != Constants.REQUEST_CODE_GET_CONTACT) {
                if (data != null) {
                    val scanData = data.getStringExtra(Constants.QR_SCAN_RESULT)
                    if (scanData != null) {
                        if (requestCode == Constants.REQUEST_CODE_SCAN_PAY_TO) {
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
                            val c2 = contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = " + id, null, null)
                            c2!!.moveToFirst()
                            val phoneIndex = c2.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER)
                            val num = c2.getString(phoneIndex)
                            this.displayRecipientAddress(num)
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

    override fun onBackPressed() {
        super.onBackPressed()
        this.finish()
    }

    companion object
}
