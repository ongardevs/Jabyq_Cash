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
import org.bitcoinj.kits.BIP47AppKit
import org.bitcoinj.wallet.Wallet
import java.io.File

class DecryptWalletActivity : AppCompatActivity() {
    lateinit var unlockPassword: EditText
    lateinit var unlockBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIManager.determineTheme(this)
        this.setContentView(R.layout.unlock_wallet)
        this.findViews()
        this.prepareViews()
        this.initListeners()
    }

    private fun prepareViews() {
    }

    private fun findViews() {
        unlockPassword = this.findViewById(R.id.unlockPassword)
        unlockBtn = this.findViewById(R.id.unlockBtn)
    }

    private fun initListeners() {
        this.unlockBtn.setOnClickListener {
            if (!TextUtils.isEmpty(unlockPassword.text)) {
                val password = unlockPassword.text.toString()
                val encryptedWallet = BIP47AppKit.getEncryptedWallet(File(applicationInfo.dataDir), Constants.WALLET_NAME)
                val scrypt = encryptedWallet.keyCrypter
                val key = scrypt!!.deriveKey(password)
                if (encryptedWallet.checkAESKey(key)) {
                    encryptedWallet.decrypt(key)
                    encryptedWallet.saveToFile(File(File(applicationInfo.dataDir), Constants.WALLET_NAME + ".wallet"))
                    val resultIntent = Intent()
                    resultIntent.putExtra(Constants.DECRYPTED_RESULT, true)
                    this.setResult(Constants.REQUEST_CODE_DECRYPT, resultIntent)
                    finish()
                } else {
                    UIManager.showToastMessage(this, "Invalid password.")
                }
            } else {
                UIManager.showToastMessage(this, "Please enter your password.")
            }
        }
    }

    override fun onBackPressed() {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    companion object
}
