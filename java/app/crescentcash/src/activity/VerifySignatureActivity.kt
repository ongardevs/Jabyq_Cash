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

class VerifySignatureActivity : AppCompatActivity() {
    lateinit var messageToVerify: EditText
    lateinit var addressToVerify: EditText
    lateinit var verifySignature: EditText
    private lateinit var btnVerifySig: Button
    private lateinit var btnScanMsgToVerify: ImageView
    private lateinit var btnScanAddrToVerify: ImageView
    private lateinit var btnScanSigToVerify: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIManager.determineTheme(this)
        this.setContentView(R.layout.verify_sig_screen)
        this.findViews()
        this.prepareViews()
        this.initListeners()
    }

    private fun prepareViews() {
    }

    private fun findViews() {
        messageToVerify = this.findViewById(R.id.messageToVerify)
        addressToVerify = this.findViewById(R.id.addressToVerify)
        verifySignature = this.findViewById(R.id.verifySignature)
        btnVerifySig = this.findViewById(R.id.btnVerifySig)
        btnScanMsgToVerify = this.findViewById(R.id.btnScanMessageToVerify)
        btnScanAddrToVerify = this.findViewById(R.id.btnScanAddressToVerify)
        btnScanSigToVerify = this.findViewById(R.id.btnScanSigToVerify)
    }

    private fun initListeners() {
        this.btnVerifySig.setOnClickListener {
            val message = messageToVerify.text.toString()
            val address = addressToVerify.text.toString()
            val signature = verifySignature.text.toString()

            if (!TextUtils.isEmpty(message) && !TextUtils.isEmpty(address) && !TextUtils.isEmpty(signature)) {
                if (address.contains("#")) {
                    object : Thread() {
                        override fun run() {
                            WalletManager.verify(this@VerifySignatureActivity, address, signature, message)
                        }
                    }.start()
                } else {
                    WalletManager.verify(this@VerifySignatureActivity, address, signature, message)
                }
            }
        }
        this.btnScanMsgToVerify.setOnClickListener { UIManager.clickScanQR(this, Constants.REQUEST_CODE_SIG_VERIFY_MESSAGE_SCAN) }
        this.btnScanAddrToVerify.setOnClickListener { UIManager.clickScanQR(this, Constants.REQUEST_CODE_SIG_VERIFY_ADDRESS_SCAN) }
        this.btnScanSigToVerify.setOnClickListener { UIManager.clickScanQR(this, Constants.REQUEST_CODE_SIG_VERIFY_SIGNATURE_SCAN) }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (data != null) {
            val scanData = data.getStringExtra(Constants.QR_SCAN_RESULT)
            if (scanData != null) {
                when (requestCode) {
                    Constants.REQUEST_CODE_SIG_VERIFY_MESSAGE_SCAN -> this.messageToVerify.setText(scanData)
                    Constants.REQUEST_CODE_SIG_VERIFY_ADDRESS_SCAN -> this.addressToVerify.setText(scanData)
                    Constants.REQUEST_CODE_SIG_VERIFY_SIGNATURE_SCAN -> this.verifySignature.setText(scanData)
                }
            }
        }
    }

    companion object
}
