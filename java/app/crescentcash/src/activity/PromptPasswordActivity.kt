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

class PromptPasswordActivity : AppCompatActivity() {
    lateinit var unlockPassword: EditText
    lateinit var unlockBtn: Button
    var confirmed: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIManager.determineTheme(this)
        this.setContentView(R.layout.prompt_pass)
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
                val scrypt = WalletManager.wallet.keyCrypter
                val key = scrypt!!.deriveKey(password)
                if (WalletManager.wallet.checkAESKey(key)) {
                    confirmed = true
                    finish()
                } else {
                    UIManager.showToastMessage(this, "Invalid password.")
                }
            } else {
                UIManager.showToastMessage(this, "Please enter your password.")
            }
        }
    }

    override fun finish() {
        val resultIntent = Intent()
        resultIntent.putExtra(Constants.CONFIRM_PASS_RESULT, confirmed)
        this.setResult(Constants.REQUEST_CODE_PROMPT_PASS_STREET, resultIntent)
        super.finish()
    }

    companion object
}
