package app.crescentcash.src.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import app.crescentcash.src.R
import app.crescentcash.src.manager.UIManager
import app.crescentcash.src.manager.WalletManager
import app.crescentcash.src.utils.Constants
import app.crescentcash.src.utils.PrefsUtil
import org.bitcoinj.crypto.BIP38PrivateKey

class AdvancedSettingsActivity : AppCompatActivity() {
    lateinit var networkNodeText: EditText
    lateinit var btnSweepPrivateKey: Button
    lateinit var btnScanPrivateKey: ImageView
    private lateinit var btnShowAddresses: Button
    private lateinit var btnShowUtxos: Button
    lateinit var addOpReturnSwitch: Switch
    lateinit var allowLegacyP2SHSwitch: Switch
    private lateinit var btnShowVerifySigScreen: Button
    private lateinit var privKeyText: EditText
    private lateinit var bip38Layout: LinearLayout
    private lateinit var bip38PrivateKeyPassword: EditText
    private var receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (Constants.ACTION_CLEAR_SWEEP_TEXT == intent.action) {
                this@AdvancedSettingsActivity.privKeyText.text = null
                this@AdvancedSettingsActivity.bip38PrivateKeyPassword.text = null
                this@AdvancedSettingsActivity.bip38Layout.visibility = View.GONE
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIManager.determineTheme(this)
        this.setContentView(R.layout.advanced_settings)
        this.findViews()
        this.prepareViews()
        this.initListeners()
    }

    private fun prepareViews() {
        this.allowLegacyP2SHSwitch.isChecked = WalletManager.allowLegacyP2SH
        this.addOpReturnSwitch.isChecked = WalletManager.addOpReturn
        this.networkNodeText.setText(PrefsUtil.prefs.getString("networkNode", "").toString())
    }

    private fun findViews() {
        networkNodeText = this.findViewById(R.id.nodeIP)
        btnSweepPrivateKey = this.findViewById(R.id.btnSweepPrivateKey)
        btnScanPrivateKey = this.findViewById(R.id.btnScanPrivateKey)
        btnShowAddresses = this.findViewById(R.id.btnShowAddresses)
        btnShowUtxos = this.findViewById(R.id.btnShowUtxos)
        allowLegacyP2SHSwitch = this.findViewById(R.id.allowLegacyP2SHSwitch)
        addOpReturnSwitch = this.findViewById(R.id.addOpReturnSwitch)
        btnShowVerifySigScreen = this.findViewById(R.id.btnShowVerifyScreen)
        privKeyText = this.findViewById(R.id.privKeyText)
        bip38Layout = this.findViewById(R.id.bip38Layout)
        bip38PrivateKeyPassword = this.findViewById(R.id.bip38PrivateKeyPassword)
    }

    private fun initListeners() {
        this.btnShowVerifySigScreen.setOnClickListener { UIManager.startActivity(this, VerifySignatureActivity::class.java) }
        this.btnScanPrivateKey.setOnClickListener { UIManager.clickScanQR(this, Constants.REQUEST_CODE_SWEEP_SCAN) }
        this.btnShowAddresses.setOnClickListener {
            val keysListActivity = Intent(this, KeysListActivity::class.java)
            startActivity(keysListActivity)
        }
        val onTextChangeListener = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                println(networkNodeText.text.toString())
                PrefsUtil.prefs.edit().putString("networkNode", networkNodeText.text.toString()).apply()
            }
        }
        this.networkNodeText.addTextChangedListener(onTextChangeListener)
        this.btnShowUtxos.setOnClickListener {
            UIManager.startActivity(this, UtxosListActivity::class.java)
        }
        this.allowLegacyP2SHSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
            WalletManager.allowLegacyP2SH = isChecked
            PrefsUtil.prefs.edit().putBoolean("allowLegacyP2SH", WalletManager.allowLegacyP2SH).apply()

        }

        this.addOpReturnSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
            WalletManager.addOpReturn = isChecked
            PrefsUtil.prefs.edit().putBoolean("addOpReturn", WalletManager.addOpReturn).apply()
        }

        this.btnSweepPrivateKey.setOnClickListener {
            val privKey = privKeyText.text.toString()
            if (!TextUtils.isEmpty(privKey)) {
                if (!WalletManager.isEncryptedBIP38Key(privKey)) {
                    WalletManager.sweepWallet(this, privKey)
                    UIManager.showToastMessage(this, "Swept wallet!")
                } else {
                    if (bip38Layout.visibility == View.GONE) {
                        bip38Layout.visibility = View.VISIBLE
                    } else {
                        val bip38Password = bip38PrivateKeyPassword.text.toString()
                        if (!TextUtils.isEmpty(bip38Password)) {
                            val encryptedKey = BIP38PrivateKey.fromBase58(WalletManager.parameters, privKey)
                            try {
                                val ecKey = encryptedKey.decrypt(bip38Password)
                                WalletManager.sweepWallet(this, ecKey.getPrivateKeyAsWiF(WalletManager.parameters))
                                UIManager.showToastMessage(this, "Swept wallet!")
                            } catch (e: BIP38PrivateKey.BadPassphraseException) {
                                UIManager.showToastMessage(this, "Incorrect password!")
                            }
                        } else {
                            UIManager.showToastMessage(this, "Please enter a password!")
                        }
                    }
                }
            }
        }
        val filter = IntentFilter()
        filter.addAction(Constants.ACTION_CLEAR_SWEEP_TEXT)
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == Constants.REQUEST_CODE_SWEEP_SCAN) {
            if (data != null) {
                val scanData = data.getStringExtra(Constants.QR_SCAN_RESULT)
                if (scanData != null) {
                    if (!WalletManager.isEncryptedBIP38Key(scanData)) {
                        this.privKeyText.setText(scanData)
                        this.bip38Layout.visibility = View.GONE
                    } else {
                        this.bip38Layout.visibility = View.VISIBLE
                        this.privKeyText.setText(scanData)
                    }
                }
            }
        }
    }

    companion object
}
