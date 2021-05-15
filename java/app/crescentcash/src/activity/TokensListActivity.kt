package app.crescentcash.src.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import app.crescentcash.src.R
import app.crescentcash.src.manager.UIManager
import app.crescentcash.src.manager.WalletManager
import app.crescentcash.src.ui.NonScrollListView
import app.crescentcash.src.utils.Constants
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.luminiasoft.ethereum.blockiesandroid.BlockiesIdenticon
import org.bitcoinj.core.slp.*
import org.bitcoinj.utils.MonetaryFormat
import org.bitcoinj.wallet.Wallet
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.set

class TokensListActivity : AppCompatActivity() {
    private lateinit var srlSLP: SwipeRefreshLayout
    private lateinit var slpList: NonScrollListView
    private lateinit var no_tx_text_slp: TextView
    private lateinit var fabSlp: FloatingActionButton
    private lateinit var fabCreate: FloatingActionButton
    private lateinit var fabReceiveSLP: FloatingActionButton
    private var isFabOpen: Boolean = false
    private var refreshingTokens: Boolean = false

    private var receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (Constants.ACTION_UPDATE_SLP_SCREEN == intent.action) {
                this@TokensListActivity.refresh()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIManager.determineTheme(this)
        this.setContentView(R.layout.slp_balances)
        this.findViews()
        this.prepareViews()
        this.initListeners()
    }

    private fun prepareViews() {
        this.refresh()
    }

    private fun findViews() {
        no_tx_text_slp = this.findViewById(R.id.no_tx_text_slp)
        fabReceiveSLP = this.findViewById(R.id.fabReceiveSLP)
        fabSlp = this.findViewById(R.id.fabSlp)
        fabCreate = this.findViewById(R.id.fabCreate)
        srlSLP = this.findViewById(R.id.srlSLP)
        slpList = this.findViewById(R.id.slpList)
    }

    private fun initListeners() {
        this.srlSLP.setOnRefreshListener { this.refresh() }
        this.fabSlp.setOnClickListener {
            if (!isFabOpen) {
                showFabMenu()
            } else {
                closeFabMenu()
            }
        }

        this.fabCreate.setOnClickListener {
            closeFabMenu()
            UIManager.startActivity(this, CreateTokenActivity::class.java)
        }
        this.fabReceiveSLP.setOnClickListener {
            closeFabMenu()
            UIManager.startActivity(this, ReceiveSLPActivity::class.java)
        }
        this.slpList.setOnItemClickListener { parent, view, position, id ->
            WalletManager.currentTokenPosition = position
            var slpToken: SlpToken? = null
            if (position != 0) {
                val tokenBalance = WalletManager.getSlpKit().slpBalances[WalletManager.currentTokenPosition - 1]
                slpToken = WalletManager.getSlpKit().getSlpToken(tokenBalance.tokenId)
                WalletManager.currentTokenId = tokenBalance.tokenId
            } else {
                WalletManager.currentTokenId = ""
            }
            val sendSlpActivity = Intent(this, SendSLPActivity::class.java)
            sendSlpActivity.putExtra(Constants.INTENT_TOKEN_TICKER_DATA, slpToken?.ticker
                    ?: WalletManager.displayUnits)
            this.startActivity(sendSlpActivity)
        }

        val filter = IntentFilter()
        filter.addAction(Constants.ACTION_UPDATE_SLP_SCREEN)
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter)
    }

    private fun refresh() {
        this.refreshSLP()
    }

    private fun showFabMenu() {
        isFabOpen = true
        fabReceiveSLP.animate().translationY(-this.resources.getDimension(R.dimen.standard_105))
        fabCreate.animate().translationY(-this.resources.getDimension(R.dimen.standard_155))
    }

    fun closeFabMenu() {
        isFabOpen = false
        fabReceiveSLP.animate().translationY(0f)
        fabCreate.animate().translationY(0f)
    }

    fun refreshSLP() {
        if(!refreshingTokens) {
            object : Thread() {
                override fun run() {
                    refreshingTokens = true
                    try {
                        WalletManager.getSlpKit().recalculateSlpUtxos()

                        this@TokensListActivity.runOnUiThread {
                            setSLPList()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()

                        this@TokensListActivity.runOnUiThread {
                            setSLPList()
                        }
                    }

                    refreshingTokens = false
                }
            }.start()
        }
        if (srlSLP.isRefreshing) srlSLP.isRefreshing = false
    }

    private fun setSLPList() {
        srlSLP.visibility = View.VISIBLE
        no_tx_text_slp.visibility = View.GONE
        WalletManager.tokenList = ArrayList()

        val datumBch = HashMap<String, String>()
        val bchTicker = WalletManager.displayUnits
        val bchHash = ""
        var bchBalance = java.lang.Double.parseDouble(WalletManager.getSlpWallet().getBalance(Wallet.BalanceType.ESTIMATED).toPlainString())
        var bchBalanceStr = ""

        when (WalletManager.displayUnits) {
            MonetaryFormat.CODE_BTC -> {
                bchBalanceStr = UIManager.formatBalanceNoUnit(bchBalance, "#,###.########")
            }
            MonetaryFormat.CODE_MBTC -> {
                bchBalance *= 1000
                bchBalanceStr = UIManager.formatBalanceNoUnit(bchBalance, "#,###.#####")
            }
            MonetaryFormat.CODE_UBTC -> {
                bchBalance *= 1000000
                bchBalanceStr = UIManager.formatBalanceNoUnit(bchBalance, "#,###.##")
            }
            "sats" -> {
                bchBalance *= 100000000
                bchBalanceStr = UIManager.formatBalanceNoUnit(bchBalance, "#,###")
            }
        }

        datumBch["tokenHash"] = bchHash
        datumBch["tokenTicker"] = bchTicker
        datumBch["balance"] = bchBalanceStr

        WalletManager.tokenList.add(datumBch)

        for (tokenBalance in WalletManager.getSlpKit().slpBalances) {
            val slpToken = WalletManager.getSlpKit().getSlpToken(tokenBalance.tokenId)

            val datum = HashMap<String, String>()
            val tokenTicker = slpToken.ticker
            val tokenHash = slpToken.tokenId
            val balance = tokenBalance.balance

            datum["tokenHash"] = tokenHash
            datum["tokenTicker"] = tokenTicker!!
            datum["balance"] = balance.toString()

            WalletManager.tokenList.add(datum)
        }

        val itemsAdapter = object : SimpleAdapter(this, WalletManager.tokenList, R.layout.list_view_activity, null, null) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                // Get the Item from ListView
                val view = LayoutInflater.from(this@TokensListActivity).inflate(R.layout.list_view_activity, null)
                val slpBlockiesAddress = WalletManager.blockieAddressFromTokenId(WalletManager.tokenList[position]["tokenHash"]
                        ?: error(""))

                val slpImage = view.findViewById<BlockiesIdenticon>(R.id.slpImage)
                val slpIcon = view.findViewById<ImageView>(R.id.slpWithIcon)
                val tokenHash = WalletManager.tokenList[position]["tokenHash"]
                val slpToken = if (tokenHash != "")
                    WalletManager.getSlpKit().getSlpToken(tokenHash)
                else
                    null

                object : Thread() {
                    override fun run() {
                        try {
                            if (slpToken != null) {
                                val exists = this@TokensListActivity.resources.getIdentifier("slp$tokenHash", "drawable", this@TokensListActivity.packageName) != 0
                                if (exists) {
                                    val drawable = this@TokensListActivity.resources.getDrawable(this@TokensListActivity.resources.getIdentifier("slp$tokenHash", "drawable", this@TokensListActivity.packageName))
                                    this@TokensListActivity.runOnUiThread {
                                        slpIcon.setImageDrawable(drawable)
                                        slpImage.visibility = View.GONE
                                        slpIcon.visibility = View.VISIBLE
                                    }
                                } else {
                                    slpImage.setAddress(slpBlockiesAddress)
                                    slpImage.setCornerRadius(128f)
                                }
                            } else {
                                val drawable = this@TokensListActivity.resources.getDrawable(this@TokensListActivity.resources.getIdentifier("logo_bch", "drawable", this@TokensListActivity.packageName))
                                this@TokensListActivity.runOnUiThread {
                                    slpIcon.setImageDrawable(drawable)
                                    slpImage.visibility = View.GONE
                                    slpIcon.visibility = View.VISIBLE
                                }
                            }
                        } catch (e: Exception) {
                            slpImage.setAddress(slpBlockiesAddress)
                            slpImage.setCornerRadius(128f)
                        }
                    }
                }.start()

                // Initialize a TextView for ListView each Item
                val text1 = view.findViewById<TextView>(R.id.text1)
                val text2 = view.findViewById<TextView>(R.id.text2)
                val text3 = view.findViewById<TextView>(R.id.text3)

                var decimalCount = 8
                when (WalletManager.displayUnits) {
                    MonetaryFormat.CODE_BTC -> {
                        decimalCount = 8
                    }
                    MonetaryFormat.CODE_MBTC -> {
                        decimalCount = 5
                    }
                    MonetaryFormat.CODE_UBTC -> {
                        decimalCount = 2
                    }
                    "sats" -> {
                        decimalCount = 0
                    }
                }

                val tokenBalString = WalletManager.tokenList[position]["balance"].toString()
                text1.text = String.format(Locale.ENGLISH, "%.${slpToken?.decimals
                        ?: decimalCount}f", java.lang.Double.parseDouble(tokenBalString))
                text3.text = WalletManager.tokenList[position]["tokenTicker"].toString()
                text2.text = slpToken?.tokenId
                // Set the text color of TextView (ListView Item)
                if (UIManager.nightModeEnabled) {
                    text1.setTextColor(Color.WHITE)
                    text2.setTextColor(Color.GRAY)
                    text3.setTextColor(Color.WHITE)
                } else {
                    text1.setTextColor(Color.BLACK)
                    text3.setTextColor(Color.BLACK)
                }

                text2.ellipsize = TextUtils.TruncateAt.END
                text2.maxLines = 1
                text2.isSingleLine = true
                // Generate ListView Item using TextView
                return view
            }
        }
        this.runOnUiThread {
            slpList.adapter = itemsAdapter
            slpList.refreshDrawableState()
        }
    }

    override fun onResume() {
        super.onResume()
        this.refresh()
    }

    companion object
}
