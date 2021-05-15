package app.crescentcash.src.activity

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import app.crescentcash.src.R
import app.crescentcash.src.manager.NetManager
import app.crescentcash.src.manager.UIManager
import app.crescentcash.src.manager.WalletManager
import app.crescentcash.src.utils.Constants
import app.crescentcash.src.utils.PrefsUtil
import org.bitcoinj.utils.MonetaryFormat
import java.net.InetSocketAddress
import java.net.Proxy
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*

class SettingsActivity : AppCompatActivity() {
    private lateinit var btnShowSeed: Button
    private lateinit var xpub: TextView
    lateinit var autoSendAmount: TextView
    lateinit var autoSendDisplayUnits: TextView
    lateinit var dropdown: Spinner
    private lateinit var btnShowTools: Button
    lateinit var nightModeSwitch: Switch
    lateinit var showFiatSwitch: Switch
    lateinit var fiatType: Spinner
    lateinit var encryptWalletSwitch: Switch
    lateinit var useTorSwitch: Switch
    private lateinit var copyXpubBtn: ImageView
    lateinit var streetModeSwitch: Switch
    private var onEncryptCheckedListener: CompoundButton.OnCheckedChangeListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIManager.determineTheme(this)
        this.setContentView(R.layout.wallet_settings)
        this.findViews()
        this.prepareViews()
        this.initListeners()
    }

    private fun prepareViews() {
        this.nightModeSwitch.isChecked = UIManager.nightModeEnabled
        this.showFiatSwitch.isChecked = UIManager.showFiat
        this.streetModeSwitch.isChecked = UIManager.streetModeEnabled
        this.encryptWalletSwitch.isChecked = WalletManager.encrypted
        this.useTorSwitch.isChecked = WalletManager.useTor
        this.autoSendAmount.text = PrefsUtil.prefs.getFloat("maximumAutomaticSend", 0.00f).toString()
    }

    private fun findViews() {
        btnShowSeed = this.findViewById(R.id.btnShowSeed)
        xpub = this.findViewById(R.id.xpub)
        autoSendAmount = this.findViewById(R.id.autoSendAmt)
        autoSendDisplayUnits = this.findViewById(R.id.autoSendDisplayUnits)
        dropdown = this.findViewById(R.id.unitDropdown)
        btnShowTools = this.findViewById(R.id.btnShowTools)
        nightModeSwitch = this.findViewById(R.id.nightModeSwitch)
        fiatType = this.findViewById(R.id.fiatType)
        showFiatSwitch = this.findViewById(R.id.showFiatSwitch)
        encryptWalletSwitch = this.findViewById(R.id.encryptWalletSwitch)
        useTorSwitch = this.findViewById(R.id.useTorSwitch)
        copyXpubBtn = this.findViewById(R.id.copyXpubBtn)
        streetModeSwitch = this.findViewById(R.id.streetModeSwitch)
    }

    private fun initListeners() {
        this.btnShowTools.setOnClickListener {
            val advancedSettingsIntent = Intent(this, AdvancedSettingsActivity::class.java)
            startActivity(advancedSettingsIntent)
        }

        this.nightModeSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
            UIManager.nightModeEnabled = isChecked
            PrefsUtil.prefs.edit().putBoolean("nightMode", UIManager.nightModeEnabled).apply()
            UIManager.determineTheme(this)
            this.recreate()
            val intent = Intent(Constants.ACTION_UPDATE_HOME_SCREEN_THEME)
            LocalBroadcastManager.getInstance(this@SettingsActivity).sendBroadcast(intent)
        }

        this.showFiatSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
            UIManager.showFiat = isChecked
            PrefsUtil.prefs.edit().putBoolean("showFiat", UIManager.showFiat).apply()
            val intent = Intent(Constants.ACTION_UPDATE_HOME_SCREEN_BALANCE)
            LocalBroadcastManager.getInstance(this@SettingsActivity).sendBroadcast(intent)
        }

        this.useTorSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
            WalletManager.useTor = isChecked

            if (isChecked) {
                NetManager.torProxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 9050))
            }

            PrefsUtil.prefs.edit().putBoolean("useTor", WalletManager.useTor).apply()
        }
        this.streetModeSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                UIManager.streetModeEnabled = isChecked
                PrefsUtil.prefs.edit().putBoolean("streetMode", UIManager.streetModeEnabled).apply()
                val intent = Intent(Constants.ACTION_UPDATE_HOME_SCREEN_BALANCE)
                LocalBroadcastManager.getInstance(this@SettingsActivity).sendBroadcast(intent)
            } else {
                if (WalletManager.encrypted) {
                    val promptPasswordActivity = Intent(this, PromptPasswordActivity::class.java)
                    promptPasswordActivity.putExtra(Constants.INTENT_PASS_PROMPT_TYPE, "streetMode")
                    startActivityForResult(promptPasswordActivity, Constants.REQUEST_CODE_PROMPT_PASS_STREET)
                } else {
                    UIManager.streetModeEnabled = isChecked
                    PrefsUtil.prefs.edit().putBoolean("streetMode", UIManager.streetModeEnabled).apply()
                    val intent = Intent(Constants.ACTION_UPDATE_HOME_SCREEN_BALANCE)
                    LocalBroadcastManager.getInstance(this@SettingsActivity).sendBroadcast(intent)
                }
            }
        }

        val decimalFormatter = DecimalFormat("#.########", DecimalFormatSymbols(Locale.US))
        this.autoSendAmount.text = decimalFormatter.format(WalletManager.maximumAutomaticSend)
        this.autoSendDisplayUnits.text = WalletManager.displayUnits

        val seed = WalletManager.wallet.keyChainSeed
        if (seed.isEncrypted) {
            UIManager.showToastMessage(this, "Wallet is encrypted!")
        } else {
            val mnemonicCode = seed.mnemonicCode
            val recoverySeedStr = StringBuilder()

            assert(mnemonicCode != null)
            for (x in mnemonicCode!!.indices) {
                recoverySeedStr.append(mnemonicCode[x]).append(" ")
            }

            this.setWalletInfo(recoverySeedStr.toString())
        }

        val unitItems = arrayOf(MonetaryFormat.CODE_BTC, MonetaryFormat.CODE_MBTC, MonetaryFormat.CODE_UBTC, "sats")
        val unitAdapter = ArrayAdapter(this, R.layout.spinner_item, unitItems)
        dropdown.adapter = unitAdapter

        when {
            WalletManager.displayUnits == MonetaryFormat.CODE_BTC -> dropdown.setSelection(0)
            WalletManager.displayUnits == MonetaryFormat.CODE_MBTC -> dropdown.setSelection(1)
            WalletManager.displayUnits == MonetaryFormat.CODE_UBTC -> dropdown.setSelection(2)
            WalletManager.displayUnits == "sats" -> dropdown.setSelection(3)
        }

        dropdown.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, v: View?, position: Int, id: Long) {
                val previous = PrefsUtil.prefs.getString("displayUnit", "BCH")

                when (position) {
                    0 -> WalletManager.displayUnits = MonetaryFormat.CODE_BTC
                    1 -> WalletManager.displayUnits = MonetaryFormat.CODE_MBTC
                    2 -> WalletManager.displayUnits = MonetaryFormat.CODE_UBTC
                    3 -> WalletManager.displayUnits = "sats"
                }

                if (WalletManager.sendType == previous)
                    WalletManager.sendType = WalletManager.displayUnits

                autoSendDisplayUnits.text = WalletManager.displayUnits

                val editor = PrefsUtil.prefs.edit()
                editor.putString("displayUnit", WalletManager.displayUnits)
                editor.putString("sendType", WalletManager.sendType)
                editor.apply()
                println(WalletManager.displayUnits)
                val intent = Intent(Constants.ACTION_UPDATE_HOME_SCREEN_BALANCE)
                LocalBroadcastManager.getInstance(this@SettingsActivity).sendBroadcast(intent)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {

            }
        }

        val fiatItems = arrayOf("USD", "EUR", "KZT")
        val fiatAdapter = ArrayAdapter(this, R.layout.spinner_item, fiatItems)
        fiatType.adapter = fiatAdapter

        when (UIManager.fiat) {
            "USD" -> fiatType.setSelection(0)
            "EUR" -> fiatType.setSelection(1)
            "KZT" -> fiatType.setSelection(2)
        }

        fiatType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, v: View?, position: Int, id: Long) {

                when (position) {
                    0 -> UIManager.fiat = "USD"
                    1 -> UIManager.fiat = "EUR"
                    2 -> UIManager.fiat = "KZT"
                }

                PrefsUtil.prefs.edit().putString("fiat", UIManager.fiat).apply()
                println(UIManager.fiat)
                val intent = Intent(Constants.ACTION_UPDATE_HOME_SCREEN_BALANCE)
                LocalBroadcastManager.getInstance(this@SettingsActivity).sendBroadcast(intent)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {

            }
        }

        this.copyXpubBtn.setOnClickListener {
            val clip: ClipData = ClipData.newPlainText("My xpub", WalletManager.getXpub())

            val clipboard = this.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
        }

        val onTextChangeListener = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {

                if (autoSendAmount.text.toString().isEmpty()) {
                    WalletManager.maximumAutomaticSend = 0.0f
                    PrefsUtil.prefs.edit().putFloat("maximumAutomaticSend", WalletManager.maximumAutomaticSend).apply()
                } else {
                    WalletManager.maximumAutomaticSend = java.lang.Float.parseFloat(autoSendAmount.text.toString())
                    PrefsUtil.prefs.edit().putFloat("maximumAutomaticSend", WalletManager.maximumAutomaticSend).apply()
                }
            }
        }
        this.autoSendAmount.addTextChangedListener(onTextChangeListener)
    }

    private fun setWalletInfo(recoverySeedStr: String) {
        this.xpub.text = WalletManager.getXpub()
        val cashAcct = PrefsUtil.prefs.getString("cashAccount", "")
        this.btnShowSeed.setOnClickListener { v -> UIManager.showAlertDialog(this, "Your recovery seed:", "$recoverySeedStr\n\nCash Account: $cashAcct", "Hide") }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (data != null) {
            if (requestCode == Constants.REQUEST_CODE_ENCRYPT_WALLET) {
                this.encryptWalletSwitch.setOnCheckedChangeListener(null)
                val encryptData = data.getBooleanExtra(Constants.ENCRYPTED_RESULT, false)
                WalletManager.encrypted = encryptData
                PrefsUtil.prefs.edit().putBoolean("useEncryption", WalletManager.encrypted).apply()
                this.encryptWalletSwitch.isChecked = encryptData
                this.encryptWalletSwitch.setOnCheckedChangeListener(onEncryptCheckedListener)
            } else {
                val promptPassData = data.getBooleanExtra(Constants.CONFIRM_PASS_RESULT, false)

                if (requestCode == Constants.REQUEST_CODE_PROMPT_PASS_STREET) {
                    UIManager.streetModeEnabled = !promptPassData
                    PrefsUtil.prefs.edit().putBoolean("streetMode", UIManager.streetModeEnabled).apply()
                    val intent = Intent(Constants.ACTION_UPDATE_HOME_SCREEN_BALANCE)
                    LocalBroadcastManager.getInstance(this@SettingsActivity).sendBroadcast(intent)
                    this.streetModeSwitch.isChecked = !promptPassData
                }
            }
        }
    }

    companion object
}
