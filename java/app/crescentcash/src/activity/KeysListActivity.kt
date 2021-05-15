package app.crescentcash.src.activity

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import app.crescentcash.src.MainActivity
import app.crescentcash.src.R
import app.crescentcash.src.manager.NetManager
import app.crescentcash.src.manager.UIManager
import app.crescentcash.src.manager.WalletManager
import app.crescentcash.src.ui.NonScrollListView
import app.crescentcash.src.utils.Constants
import app.crescentcash.src.utils.PrefsUtil
import com.github.kiulian.converter.AddressConverter
import org.bitcoinj.core.CashAddress
import org.bitcoinj.core.ECKey
import org.bitcoinj.crypto.BIP38PrivateKey
import java.util.*
import kotlin.collections.set

class KeysListActivity : AppCompatActivity() {
    private lateinit var srlKeys: SwipeRefreshLayout
    private lateinit var keysList: NonScrollListView
    lateinit var keyToImportText: EditText
    private lateinit var btnImportKey: Button
    private lateinit var btnImportKeyScan: ImageView
    lateinit var bip38ImportKeyPassword: EditText
    lateinit var bip38ImportLayout: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MainActivity.dataDirectory = applicationInfo.dataDir
        UIManager.determineTheme(this)
        this.setContentView(R.layout.keys_screen)
        this.findViews()
        this.prepareViews()
        this.initListeners()
    }

    private fun prepareViews() {
        this.refreshKeys()
    }

    private fun findViews() {
        srlKeys = this.findViewById(R.id.srlKeys)
        keysList = this.findViewById(R.id.keysList)
        keyToImportText = this.findViewById(R.id.keyToImportText)
        btnImportKey = this.findViewById(R.id.btnImportKey)
        btnImportKeyScan = this.findViewById(R.id.btnImportKeyScan)
        bip38ImportKeyPassword = this.findViewById(R.id.bip38ImportKeyPassword)
        bip38ImportLayout = this.findViewById(R.id.bip38ImportLayout)
    }

    private fun initListeners() {
        this.srlKeys.setOnRefreshListener { this.refreshKeys() }
        this.btnImportKeyScan.setOnClickListener { UIManager.clickScanQR(this, Constants.REQUEST_CODE_IMPORT_SCAN) }
        this.keysList.setOnItemClickListener { parent, view, position, id ->
            WalletManager.currentEcKey = WalletManager.issuedKeysArrayList[position]["key"]
                    ?: error("")
            UIManager.startActivity(this, ManageKeyActivity::class.java)
        }
        this.btnImportKey.setOnClickListener {
            val privKey = this.keyToImportText.text.toString()
            if (!TextUtils.isEmpty(privKey)) {
                if (!WalletManager.isEncryptedBIP38Key(privKey)) {
                    WalletManager.importPrivateKey(privKey)
                    UIManager.showToastMessage(this, "Imported key!")
                    this.keyToImportText.text = null
                    this.bip38ImportLayout.visibility = View.GONE
                    refreshKeys()
                } else {
                    if (this.bip38ImportLayout.visibility == View.GONE) {
                        this.bip38ImportLayout.visibility = View.VISIBLE
                    } else {
                        val bip38Password = this.bip38ImportKeyPassword.text.toString()
                        if (!TextUtils.isEmpty(bip38Password)) {
                            val encryptedKey = BIP38PrivateKey.fromBase58(WalletManager.parameters, privKey)
                            try {
                                val ecKey = encryptedKey.decrypt(bip38Password)
                                WalletManager.importPrivateKey(ecKey.getPrivateKeyAsWiF(WalletManager.parameters))
                                UIManager.showToastMessage(this, "Imported key!")
                                this.keyToImportText.text = null
                                this.bip38ImportKeyPassword.text = null
                                this.bip38ImportLayout.visibility = View.GONE
                                refreshKeys()
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
        this.keysList.setOnItemLongClickListener { parent, view, position, id ->
            val key = WalletManager.issuedKeysArrayList[position]["key"] ?: error("")
            val cashAddress = CashAddress.fromBase58(WalletManager.parameters, key.toAddress(WalletManager.parameters).toString())
            val clip = ClipData.newPlainText("Address", cashAddress.toString())
            val clipboard = this.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Copied address", Toast.LENGTH_SHORT).show()
            true
        }
    }

    fun refreshKeys() {
        setKeysList()

        if (srlKeys.isRefreshing) srlKeys.isRefreshing = false
    }

    private fun setKeysList() {
        if (WalletManager.wallet.issuedReceiveKeys.isNotEmpty()) {
            WalletManager.issuedKeysArrayList = ArrayList()
            for (key in WalletManager.wallet.issuedReceiveKeys) {
                val datum = HashMap<String, ECKey>()
                datum["key"] = key
                WalletManager.issuedKeysArrayList.add(datum)
            }

            for (key in WalletManager.wallet.importedKeys) {
                val datum = HashMap<String, ECKey>()
                datum["key"] = key
                WalletManager.issuedKeysArrayList.add(datum)
            }

            val itemsAdapter = object : SimpleAdapter(this, WalletManager.issuedKeysArrayList, R.layout.key_cell, null, null) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = LayoutInflater.from(parent.context).inflate(R.layout.key_cell, null)
                    val ecKey = WalletManager.issuedKeysArrayList[position]["key"]
                    if (ecKey != null) {
                        val legacyAddr = ecKey.toAddress(WalletManager.parameters).toString()
                        val params = WalletManager.parameters
                        val cashAddrObj = CashAddress.fromBase58(WalletManager.parameters, legacyAddr)
                        val cashAddr = cashAddrObj.toString().replace(params.cashAddrPrefix + ":", "")
                        val cashAcctInternal = PrefsUtil.prefs.getString("cashacct_$legacyAddr", "")
                        val cashAcctTx = PrefsUtil.prefs.getString("cashacct_tx_$legacyAddr", "")

                        if (position == 0) {
                            setCashAcctText(view, PrefsUtil.prefs.getString("cashAccount", "").toString())
                        } else {
                            if (cashAcctInternal.toString().endsWith("#???")) {
                                object : Thread() {
                                    override fun run() {
                                        val cashAcct = NetManager.checkForCashAccount(ecKey, cashAcctTx.toString(), cashAcctInternal.toString().replace("#???", ""))
                                        setCashAcctText(view, cashAcct)
                                    }
                                }.start()
                            } else {
                                if (cashAcctInternal == "") {
                                    object : Thread() {
                                        override fun run() {
                                            val cashAcct = NetManager.reverseLookupCashAccount(cashAddr, legacyAddr)
                                            setCashAcctText(view, cashAcct)
                                        }
                                    }.start()
                                } else {
                                    val cashAcct = if (cashAcctInternal == "none_found") {
                                        "No Cash Account"
                                    } else {
                                        cashAcctInternal
                                    }

                                    setCashAcctText(view, cashAcct!!)
                                }
                            }
                        }

                        setAddressText(view, cashAddr)
                    }

                    return view
                }
            }
            this.runOnUiThread {
                keysList.adapter = itemsAdapter
                keysList.refreshDrawableState()
            }
        }
    }

    private fun setCashAcctText(view: View, cashAcct: String) {
        val cashAcctTextView = view.findViewById(R.id.text2) as TextView
        cashAcctTextView.text = cashAcct

        if (UIManager.nightModeEnabled) {
            cashAcctTextView.setTextColor(Color.GRAY)
        }
    }

    private fun setAddressText(view: View, cashAddr: String) {
        val addressTextView = view.findViewById(R.id.text1) as TextView
        addressTextView.text = cashAddr

        if (UIManager.nightModeEnabled) {
            addressTextView.setTextColor(Color.WHITE)
        } else {
            addressTextView.setTextColor(Color.BLACK)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (data != null) {
            val scanData = data.getStringExtra(Constants.QR_SCAN_RESULT)
            if (scanData != null) {
                if (requestCode == Constants.REQUEST_CODE_IMPORT_SCAN) {
                    if (!WalletManager.isEncryptedBIP38Key(scanData)) {
                        this.keyToImportText.setText(scanData)
                        this.bip38ImportLayout.visibility = View.GONE
                    } else {
                        bip38ImportLayout.visibility = View.VISIBLE
                        this.keyToImportText.setText(scanData)
                    }

                }
            }
        }
    }

    companion object
}
