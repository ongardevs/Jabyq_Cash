package app.crescentcash.src.activity

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import app.crescentcash.src.R
import app.crescentcash.src.manager.UIManager
import app.crescentcash.src.manager.WalletManager
import app.crescentcash.src.utils.Constants
import org.bitcoinj.crypto.KeyCrypterScrypt

class EncryptWalletActivity : AppCompatActivity() {
    lateinit var encryptBtn: Button
    lateinit var encryptionPassword: EditText
    lateinit var encryptionPwdConfirm: EditText
    var encrypted: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIManager.determineTheme(this)
        this.setContentView(R.layout.encrypt_screen)
        this.findViews()
        this.prepareViews()
        this.initListeners()
    }

    private fun prepareViews() {
    }

    private fun findViews() {
        encryptBtn = this.findViewById(R.id.encryptBtn)
        encryptionPassword = this.findViewById(R.id.encryptionPassword)
        encryptionPwdConfirm = this.findViewById(R.id.encryptionPwdConfirm)
    }

    private fun initListeners() {
        this.encryptBtn.setOnClickListener {

            if (!TextUtils.isEmpty(encryptionPassword.text) && !TextUtils.isEmpty(encryptionPwdConfirm.text)) {
                if (encryptionPassword.text.toString() == encryptionPwdConfirm.text.toString()) {
                    UIManager.showToastMessage(this, "Encrypting...")
                    val password = encryptionPassword.text.toString()
                    val scrypt = KeyCrypterScrypt(WalletManager.SCRYPT_PARAMETERS)
                    val key = scrypt.deriveKey(password)
                    WalletManager.wallet.encrypt(scrypt, key)
                    UIManager.showToastMessage(this, "Encrypted wallet!")
                    //WalletManager.aesKey = key
                    encrypted = true
                    finish()
                } else {
                    UIManager.showToastMessage(this, "Passwords do not match.")
                }
            } else {
                UIManager.showToastMessage(this, "Please enter a password.")
            }
        }
    }

    override fun finish() {
        val resultIntent = Intent()
        resultIntent.putExtra(Constants.ENCRYPTED_RESULT, encrypted)
        this.setResult(Constants.REQUEST_CODE_ENCRYPT_WALLET, resultIntent)
        super.finish()
    }

    companion object
}
