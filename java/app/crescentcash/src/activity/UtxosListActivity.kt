package app.crescentcash.src.activity

import android.graphics.Color
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import android.widget.ListView
import android.widget.SimpleAdapter
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.get
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import app.crescentcash.src.R
import app.crescentcash.src.manager.UIManager
import app.crescentcash.src.manager.WalletManager
import app.crescentcash.src.ui.NonScrollListView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.bitcoinj.core.TransactionOutput
import org.bitcoinj.utils.MonetaryFormat
import org.bitcoinj.wallet.Wallet
import java.util.*

class UtxosListActivity : AppCompatActivity() {
    private lateinit var srlUtxo: SwipeRefreshLayout
    private lateinit var utxoListView: NonScrollListView
    private lateinit var btnSendUtxos: FloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIManager.determineTheme(this)
        this.setContentView(R.layout.utxos)
        this.findViews()
        this.prepareViews()
        this.initListeners()
    }

    private fun prepareViews() {
        this.setUtxoList(WalletManager.wallet)
    }

    private fun findViews() {
        utxoListView = this.findViewById(R.id.utxoListView)
        btnSendUtxos = this.findViewById(R.id.btnSendUtxos)
        srlUtxo = this.findViewById(R.id.srlUtxos)
    }

    private fun initListeners() {
        this.srlUtxo.setOnRefreshListener { this.setUtxoList(WalletManager.wallet) }
        this.btnSendUtxos.setOnClickListener {
            UIManager.startActivity(this, SendActivity::class.java)
        }
        this.utxoListView.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        this.utxoListView.setOnItemClickListener { parent, view, position, id ->
            val cell: CheckedTextView = utxoListView[position].findViewById(R.id.text1)
            cell.isChecked = !cell.isChecked
            val utxo = WalletManager.utxoMap[position]["utxo"]
            println("Selected utxo: $utxo")
            if (cell.isChecked) {
                WalletManager.selectedUtxos.add(utxo!!)
            } else {
                WalletManager.selectedUtxos.remove(utxo)
            }

            println(WalletManager.selectedUtxos.toString())
        }
    }

    private fun setUtxoList(wallet: Wallet) {
        setUtxoListView(wallet)

        if (srlUtxo.isRefreshing) srlUtxo.isRefreshing = false
    }

    private fun setUtxoListView(wallet: Wallet?) {
        if (wallet != null) {
            val utxos = wallet.utxos
            WalletManager.utxoMap.clear()

            for (x in 0 until utxos.size) {
                val utxo = utxos[x]
                val utxoMapped = HashMap<String, TransactionOutput>()
                utxoMapped["utxo"] = utxo
                WalletManager.utxoMap.add(utxoMapped)
            }

            if (WalletManager.utxoMap.size > 0) {
                val itemsAdapter = object : SimpleAdapter(this, WalletManager.utxoMap, R.layout.utxo_cell, null, null) {
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                        // Get the Item from ListView
                        val view = LayoutInflater.from(parent.context).inflate(R.layout.utxo_cell, null)
                        val utxo = WalletManager.utxoMap[position]["utxo"]

                        // Initialize a TextView for ListView each Item
                        val hash = view.findViewById(R.id.text1) as CheckedTextView
                        val amountView = view.findViewById(R.id.utxoAmount) as TextView

                        var amount = utxo!!.value.toPlainString()
                        val txHash = utxo.parentTransactionHash.toString()
                        val index = utxo.index
                        var balBch = java.lang.Double.parseDouble(amount)

                        hash.text = "$txHash:$index"

                        val unit = WalletManager.displayUnits
                        when (unit) {
                            MonetaryFormat.CODE_BTC -> {
                                amount = UIManager.formatBalanceNoUnit(balBch, "#,###.########")
                            }
                            MonetaryFormat.CODE_MBTC -> {
                                balBch *= 1000
                                amount = UIManager.formatBalanceNoUnit(balBch, "#,###.#####")
                            }
                            MonetaryFormat.CODE_UBTC -> {
                                balBch *= 1000000
                                amount = UIManager.formatBalanceNoUnit(balBch, "#,###.##")
                            }
                            "sats" -> {
                                balBch *= 100000000
                                amount = UIManager.formatBalanceNoUnit(balBch, "#,###")
                            }
                            else -> amount = UIManager.formatBalanceNoUnit(balBch, "#,###.########")
                        }

                        amountView.text = amount

                        // Set the text color of TextView (ListView Item)

                        if (UIManager.nightModeEnabled) {
                            hash.setTextColor(Color.WHITE)
                            amountView.setTextColor(Color.GRAY)
                        } else {
                            hash.setTextColor(Color.BLACK)
                        }

                        hash.ellipsize = TextUtils.TruncateAt.MIDDLE
                        hash.maxLines = 1
                        hash.isSingleLine = true
                        // Generate ListView Item using TextView
                        return view
                    }
                }
                this.runOnUiThread { utxoListView.adapter = itemsAdapter }
            } else {
                this.findViewById<TextView>(R.id.no_utxo_text).visibility = View.VISIBLE
                this.findViewById<SwipeRefreshLayout>(R.id.srlUtxos).visibility = View.GONE
            }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        this.finish()
    }

    override fun finish() {
        WalletManager.selectedUtxos = ArrayList()
        println(WalletManager.selectedUtxos)
        super.finish()
    }

    companion object
}
