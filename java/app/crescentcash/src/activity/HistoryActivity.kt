package app.crescentcash.src.activity

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.SimpleAdapter
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import app.crescentcash.src.R
import app.crescentcash.src.manager.UIManager
import app.crescentcash.src.manager.WalletManager
import app.crescentcash.src.ui.NonScrollListView
import app.crescentcash.src.utils.Constants
import app.crescentcash.src.utils.PrefsUtil
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.bitcoinj.utils.MonetaryFormat
import org.bitcoinj.wallet.Wallet
import java.util.*

class HistoryActivity : AppCompatActivity() {
    private lateinit var fab: FloatingActionButton
    private lateinit var fabSend: FloatingActionButton
    private lateinit var fabReceive: FloatingActionButton
    private lateinit var txHistoryList: NonScrollListView

    private lateinit var no_tx_text: TextView
    private lateinit var srlHistory: SwipeRefreshLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIManager.determineTheme(this)
        this.setContentView(R.layout.history)
        this.findViews()
        this.prepareViews()
        this.initListeners()
    }

    private fun prepareViews() {
        this.setArrayAdapter(WalletManager.wallet)
    }

    private fun findViews() {
        fab = this.findViewById(R.id.fab)
        fabSend = this.findViewById(R.id.fabSend)
        fabReceive = this.findViewById(R.id.fabReceive)
        txHistoryList = this.findViewById(R.id.txHistoryList)
        no_tx_text = this.findViewById(R.id.no_tx_text)
        srlHistory = this.findViewById(R.id.srlHistory)
    }

    private fun initListeners() {
        this.fab.setOnClickListener {
            if (!isFabOpen) {
                showFabMenu()
            } else {
                closeFabMenu()
            }
        }

        this.fabSend.setOnClickListener { startSendActivity() }
        this.fabReceive.setOnClickListener { displayReceive() }
        this.txHistoryList.setOnItemClickListener { parent, view, position, id ->
            val viewTxActivity = Intent(this, ViewTransactionActivity::class.java)
            viewTxActivity.putExtra(Constants.INTENT_TRANSACTION_POSITION_DATA, position)
            this.startActivity(viewTxActivity)
        }
        this.srlHistory.setOnRefreshListener { setArrayAdapter(WalletManager.wallet) }
    }

    private var isFabOpen: Boolean = false

    private fun startSendActivity() {
        closeFabMenu()
        UIManager.startActivity(this, SendActivity::class.java)
    }

    private fun showFabMenu() {
        isFabOpen = true
        fabReceive.animate().translationY(-this.resources.getDimension(R.dimen.standard_105))
        fabSend.animate().translationY(-this.resources.getDimension(R.dimen.standard_155))
    }

    fun closeFabMenu() {
        isFabOpen = false
        fabReceive.animate().translationY(0f)
        fabSend.animate().translationY(0f)
    }

    private fun displayReceive() {
        closeFabMenu()
        val cashAccount = PrefsUtil.prefs.getString("cashAccount", "")
        val receiveActivity = Intent(this, ReceiveActivity::class.java)
        receiveActivity.putExtra(Constants.INTENT_CASH_ACCOUNT_DATA, cashAccount)
        this.startActivity(receiveActivity)
    }

    private fun setArrayAdapter(wallet: Wallet) {
        setListViewShit(wallet)

        if (srlHistory.isRefreshing) srlHistory.isRefreshing = false
    }

    private fun setListViewShit(wallet: Wallet?) {
        if (wallet != null) {
            val txListFromWallet = wallet.getRecentTransactions(0, false)

            if (txListFromWallet != null && txListFromWallet.size != 0) {
                val txListFormatted = ArrayList<Map<String, String>>()
                WalletManager.txList = ArrayList()

                if (txListFromWallet.size > 0) {
                    no_tx_text.visibility = View.GONE
                    srlHistory.visibility = View.VISIBLE

                    for (x in 0 until txListFromWallet.size) {
                        val tx = txListFromWallet[x]
                        val confirmations = tx.confidence.depthInBlocks
                        val value = tx.getValue(wallet)
                        val datum = HashMap<String, String>()
                        var amountDbl = java.lang.Double.parseDouble(value.toPlainString().replace("-", ""))
                        val unit = WalletManager.displayUnits
                        var amountStr = ""

                        if (value.isPositive) {
                            when (unit) {
                                MonetaryFormat.CODE_BTC -> {
                                    amountStr = UIManager.formatBalance(amountDbl, "#,###.########")
                                }
                                MonetaryFormat.CODE_MBTC -> {
                                    amountDbl *= 1000
                                    amountStr = UIManager.formatBalance(amountDbl, "#,###.#####")
                                }
                                MonetaryFormat.CODE_UBTC -> {
                                    amountDbl *= 1000000
                                    amountStr = UIManager.formatBalance(amountDbl, "#,###.##")
                                }
                                "sats" -> {
                                    amountDbl *= 100000000
                                    amountStr = UIManager.formatBalance(amountDbl, "#,###")
                                }
                            }

                            datum["cashaccount"] = "false"
                            datum["cashshuffle"] = "false"
                            datum["slptx"] = "false"
                            val satoshiDice = WalletManager.isProtocol(tx, "02446365")
                            datum["satoshidice"] = satoshiDice.toString()
                            datum["action"] = "received"
                            val entry = String.format("%5s", amountStr)
                            datum["amount"] = entry
                            txListFormatted.add(datum)
                            WalletManager.txList.add(tx)
                        }

                        if (value.isNegative) {
                            when (unit) {
                                MonetaryFormat.CODE_BTC -> {
                                    amountStr = UIManager.formatBalance(amountDbl, "#,###.########")
                                }
                                MonetaryFormat.CODE_MBTC -> {
                                    amountDbl *= 1000
                                    amountStr = UIManager.formatBalance(amountDbl, "#,###.#####")
                                }
                                MonetaryFormat.CODE_UBTC -> {
                                    amountDbl *= 1000000
                                    amountStr = UIManager.formatBalance(amountDbl, "#,###.##")
                                }
                                "sats" -> {
                                    amountDbl *= 100000000
                                    amountStr = UIManager.formatBalance(amountDbl, "#,###")
                                }
                            }

                            datum["cashaccount"] = WalletManager.isProtocol(tx, "01010101").toString()
                            datum["cashshuffle"] = WalletManager.isCashShuffle(tx).toString()
                            datum["slptx"] = WalletManager.isProtocol(tx, "534c5000").toString()
                            datum["satoshidice"] = WalletManager.sentToSatoshiDice(tx).toString()
                            datum["action"] = "sent"
                            val entry = String.format("%5s", amountStr)
                            datum["amount"] = entry
                            txListFormatted.add(datum)
                            WalletManager.txList.add(tx)
                        }

                        when {
                            confirmations == 0 -> datum["confirmations"] = "0/unconfirmed"
                            confirmations < 6 -> datum["confirmations"] = "$confirmations/6 confirmations"
                            else -> datum["confirmations"] = "6+ confirmations"
                        }
                    }

                    val itemsAdapter = object : SimpleAdapter(this, txListFormatted, R.layout.transaction_list_cell, null, null) {
                        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                            // Get the Item from ListView
                            val view = LayoutInflater.from(this@HistoryActivity).inflate(R.layout.transaction_list_cell, null)
                            val action = txListFormatted[position]["action"]
                            val satoshiDice = txListFormatted[position]["satoshidice"]

                            if (action == "sent") {
                                val slptx = txListFormatted[position]["slptx"]
                                val cashShuffle = txListFormatted[position]["cashshuffle"]
                                val cashacct = txListFormatted[position]["cashaccount"]

                                if (slptx == "true") {
                                    val slpTxImg = view.findViewById<ImageView>(R.id.slptx)
                                    slpTxImg.visibility = View.VISIBLE
                                } else if (cashShuffle == "true") {
                                    val cashShuffleImg = view.findViewById<ImageView>(R.id.cashshuffle_icon)
                                    cashShuffleImg.visibility = View.VISIBLE
                                } else if (cashacct == "true") {
                                    val cashAcctImg = view.findViewById<ImageView>(R.id.cashacct_icon)
                                    cashAcctImg.visibility = View.VISIBLE
                                }
                            } else if (action == "received") {
                                view.findViewById<ImageView>(R.id.send).visibility = View.GONE
                                view.findViewById<ImageView>(R.id.receive).visibility = View.VISIBLE

                            }

                            if (satoshiDice == "true") {
                                val satoshiDiceImg = view.findViewById<ImageView>(R.id.satoshidice)
                                satoshiDiceImg.visibility = View.VISIBLE
                            }

                            // Initialize a TextView for ListView each Item
                            val text1 = view.findViewById(R.id.text1) as TextView
                            val text2 = view.findViewById(R.id.text2) as TextView

                            val amount = txListFormatted[position]["amount"]
                            val confirmations = txListFormatted[position]["confirmations"]
                            text1.text = amount.toString()
                            text2.text = confirmations.toString()
                            // Set the text color of TextView (ListView Item)

                            if (UIManager.nightModeEnabled) {
                                text1.setTextColor(Color.WHITE)
                                text2.setTextColor(Color.GRAY)
                            } else {
                                text1.setTextColor(Color.BLACK)
                            }

                            text2.ellipsize = TextUtils.TruncateAt.END
                            text2.maxLines = 1
                            text2.isSingleLine = true
                            // Generate ListView Item using TextView
                            return view
                        }
                    }
                    this.runOnUiThread { txHistoryList.adapter = itemsAdapter }
                } else {
                    srlHistory.visibility = View.GONE
                    no_tx_text.visibility = View.VISIBLE
                }
            } else {
                srlHistory.visibility = View.GONE
                no_tx_text.visibility = View.VISIBLE
            }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        this.finish()
    }

    companion object
}
