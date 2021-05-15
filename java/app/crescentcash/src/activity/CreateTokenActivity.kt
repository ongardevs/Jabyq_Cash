package app.crescentcash.src.activity

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import app.crescentcash.src.R
import app.crescentcash.src.manager.UIManager
import app.crescentcash.src.manager.WalletManager
import app.crescentcash.src.utils.Constants
import org.bitcoinj.core.InsufficientMoneyException

class CreateTokenActivity : AppCompatActivity() {
    lateinit var tokenName: EditText
    lateinit var tokenTicker: EditText
    lateinit var tokenDecimals: EditText
    lateinit var tokenAmount: EditText
    private lateinit var btnCreateToken: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIManager.determineTheme(this)
        this.setContentView(R.layout.create_token_screen)
        this.findViews()
        this.prepareViews()
        this.initListeners()
    }

    private fun prepareViews() {
    }

    private fun findViews() {
        tokenName = this.findViewById(R.id.tokenName)
        tokenTicker = this.findViewById(R.id.tokenTicker)
        tokenDecimals = this.findViewById(R.id.tokenDecimals)
        tokenAmount = this.findViewById(R.id.tokenAmount)
        btnCreateToken = this.findViewById(R.id.btnCreateToken)
    }

    private fun initListeners() {
        this.btnCreateToken.setOnClickListener {
            val name = tokenName.text.toString()
            val ticker = tokenTicker.text.toString()
            val decimals = tokenDecimals.text.toString()
            val amount = tokenAmount.text.toString()

            if (!TextUtils.isEmpty(name) && !TextUtils.isEmpty(ticker) && !TextUtils.isEmpty(decimals) && !TextUtils.isEmpty(amount)) {
                if(decimals.toInt() > 9 || decimals.toInt() < 0) {
                    UIManager.showToastMessage(this@CreateTokenActivity, "Decimal count cannot be outside 0-9")
                    tokenDecimals.text = null
                } else {
                    try {
                        val tx = WalletManager.getSlpKit().createSlpGenesisTransaction(ticker, name, "", decimals.toInt(), amount.toLong(), null)
                        WalletManager.getSlpKit().broadcastSlpTransaction(tx)
                        clearFields()
                        UIManager.showToastMessage(this@CreateTokenActivity, "Token created!")
                    } catch (e: InsufficientMoneyException) {
                        UIManager.showToastMessage(this@CreateTokenActivity, "Insufficient money to create token!")
                        clearFields()
                    }
                }
            }
        }
    }

    private fun clearFields () {
        tokenName.text = null
        tokenTicker.text = null
        tokenDecimals.text = null
        tokenAmount.text = null
    }

    companion object
}
