package app.crescentcash.src.activity

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import app.crescentcash.src.R
import app.crescentcash.src.manager.UIManager
import app.crescentcash.src.manager.WalletManager
import app.crescentcash.src.utils.PrefsUtil

class ManageKeyActivity : AppCompatActivity() {
    lateinit var messageToSignText: EditText
    private lateinit var signMessageBtn: Button
    lateinit var signatureText: TextView
    private lateinit var btnCopyPrivateKey: Button
    lateinit var newCashAcctName: EditText
    private lateinit var registerCashAcctBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIManager.determineTheme(this)
        this.setContentView(R.layout.manage_key_screen)
        this.findViews()
        this.prepareViews()
        this.initListeners()
    }

    private fun prepareViews() {
    }

    private fun findViews() {
        messageToSignText = this.findViewById(R.id.messageToSign)
        signMessageBtn = this.findViewById(R.id.signMessageBtn)
        signatureText = this.findViewById(R.id.msgSigText)
        btnCopyPrivateKey = this.findViewById(R.id.btnCopyPrivateKey)
        newCashAcctName = this.findViewById(R.id.newCashAcctName)
        registerCashAcctBtn = this.findViewById(R.id.registerCashAcctBtn)
    }

    private fun initListeners() {
        this.signMessageBtn.setOnClickListener {
            if (!TextUtils.isEmpty(messageToSignText.text.toString())) {
                val signature = WalletManager.signMessageWithKey(WalletManager.currentEcKey, messageToSignText.text.toString())
                signatureText.text = signature
            }
        }
        this.btnCopyPrivateKey.setOnClickListener {
            val clip = ClipData.newPlainText("Address", WalletManager.currentEcKey.getPrivateKeyAsWiF(WalletManager.parameters))
            val clipboard = this.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Copied private key", Toast.LENGTH_SHORT).show()
        }
        this.registerCashAcctBtn.setOnClickListener {
            if (!TextUtils.isEmpty(newCashAcctName.text.toString())) {
                val key = WalletManager.currentEcKey
                val address = key.toAddress(WalletManager.parameters).toString()
                val txHash = WalletManager.registerCashAccount(key, newCashAcctName.text.toString())

                if(txHash != null) {
                    val editor = PrefsUtil.prefs.edit()
                    editor.putString("cashacct_$address", "${newCashAcctName.text}#???")
                    editor.putString("cashacct_tx_$address", txHash)
                    editor.apply()
                }
                newCashAcctName.text = null
            }
        }
    }

    companion object
}
