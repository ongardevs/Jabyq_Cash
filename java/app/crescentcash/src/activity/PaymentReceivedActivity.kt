package app.crescentcash.src.activity

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import app.crescentcash.src.R
import app.crescentcash.src.manager.UIManager


class PaymentReceivedActivity : AppCompatActivity() {
    lateinit var btnAckTx: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIManager.determineTheme(this)
        this.setContentView(R.layout.received_screen)
        this.findViews()
        this.initListeners()
    }

    private fun findViews() {
        btnAckTx = this.findViewById(R.id.btnAckTx)
    }

    private fun initListeners() {
        this.btnAckTx.setOnClickListener { this.finish() }

    }

    override fun onBackPressed() {
        super.onBackPressed()
        this.finish()
    }
}
