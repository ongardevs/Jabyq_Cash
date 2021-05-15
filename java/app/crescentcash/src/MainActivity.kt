package app.crescentcash.src

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.*
import androidx.annotation.UiThread
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import app.crescentcash.src.activity.*
import app.crescentcash.src.manager.NetManager
import app.crescentcash.src.manager.UIManager
import app.crescentcash.src.manager.WalletManager
import app.crescentcash.src.utils.Constants
import app.crescentcash.src.utils.PermissionHelper
import app.crescentcash.src.utils.PrefsUtil
import org.bitcoinj.kits.BIP47AppKit
import org.bitcoinj.utils.MonetaryFormat
import org.bitcoinj.wallet.Wallet
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.File
import java.security.Provider
import java.security.Security
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*


class MainActivity : AppCompatActivity() {
    private lateinit var createWalletBtn: Button
    private lateinit var restoreWalletBtn: Button
    lateinit var restore_wallet: FrameLayout
    lateinit var new_wallet: FrameLayout
    lateinit var newuser: FrameLayout
    private lateinit var balance: TextView
    private lateinit var balanceSlp: TextView
    private lateinit var openKeys: ImageButton
    private lateinit var registerUserBtn: Button
    lateinit var handle: EditText
    private lateinit var verifyUserBtn: Button
    lateinit var recoverySeed: EditText
    lateinit var handle2: EditText
    private lateinit var btnViewHistory: ImageButton
    private lateinit var syncPct: TextView
    private lateinit var syncPctSlp: TextView
    private lateinit var btnViewTokens: ImageButton
    private lateinit var receiveFabHome: ImageButton
    private lateinit var receiveSlpHome: ImageButton
    private lateinit var sendFabHome: ImageButton
    private lateinit var fiatBalTxt: TextView
    private lateinit var fiatBalTxtSlp: TextView

    private var receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (Constants.ACTION_UPDATE_HOME_SCREEN_BALANCE == intent.action) {
                this@MainActivity.refresh()

                if (!UIManager.isDisplayingDownload) {
                    this@MainActivity.syncPct.text = ""
                }

                if (!WalletManager.downloadingSlp) {
                    this@MainActivity.syncPctSlp.text = ""
                }
            }
            if (Constants.ACTION_UPDATE_HOME_SCREEN_THEME == intent.action) {
                UIManager.determineTheme(this@MainActivity)
                this@MainActivity.setContentView(R.layout.activity_main2)
                this@MainActivity.findViews()
                this@MainActivity.prepareViews()
                this@MainActivity.initListeners()
                this@MainActivity.refresh()

                if (!UIManager.isDisplayingDownload) {
                    this@MainActivity.syncPct.text = ""
                }

                if (!WalletManager.downloadingSlp) {
                    this@MainActivity.syncPctSlp.text = ""
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupBouncyCastle()
        dataDirectory = applicationInfo.dataDir
        PrefsUtil.prefs = getSharedPreferences("app.crescentcash.src", Context.MODE_PRIVATE)
        PrefsUtil.loadPrefs()
        UIManager.determineTheme(this)
        this.setContentView(R.layout.activity_main2)
        this.findViews()
        this.prepareViews()
        this.initListeners()
        NetManager.establishProxy()

        isNewUser = !File(this.applicationInfo.dataDir + "/users_wallet.wallet").exists()

        if (isNewUser) {
            this.newuser.visibility = View.VISIBLE
        } else {
            if (savedInstanceState == null && WalletManager.walletKit == null) {
                if(BIP47AppKit.isWalletEncrypted(WalletManager.walletDir, Constants.WALLET_NAME)) {
                    /*
                    If the saved setting we got is false, but our wallet is encrypted, then we set our saved setting to true.
                     */
                    if (!WalletManager.encrypted) {
                        WalletManager.encrypted = true
                        PrefsUtil.prefs.edit().putBoolean("useEncryption", WalletManager.encrypted).apply()
                    }

                    val decryptIntent = Intent(this, DecryptWalletActivity::class.java)
                    this.startActivityForResult(decryptIntent, Constants.REQUEST_CODE_DECRYPT)
                } else {
                    this.startWalletAndChecks();
                }
            } else {
                val intent = Intent(Constants.ACTION_UPDATE_HOME_SCREEN_BALANCE)
                LocalBroadcastManager.getInstance(this@MainActivity).sendBroadcast(intent)
            }
        }

        if (!UIManager.isDisplayingDownload) {
            this@MainActivity.syncPct.text = ""
        }

        if (!WalletManager.downloadingSlp) {
            this@MainActivity.syncPctSlp.text = ""
        }
    }

    private fun startWalletAndChecks() {
        usingNewBlockStore = PrefsUtil.prefs.getBoolean("usingNewBlockStore", false)
        usingNewBlockStoreSlp = PrefsUtil.prefs.getBoolean("usingNewBlockStoreSlp", false)

        if(!usingNewBlockStore) {
            val chainFile = File(WalletManager.walletDir, "${Constants.WALLET_NAME}.spvchain")
            if(chainFile.exists()) {
                chainFile.delete()
            }
            PrefsUtil.prefs.edit().putBoolean("usingNewBlockStore", true).apply()
        }

        if(!usingNewBlockStoreSlp) {
            val chainFile = File(WalletManager.walletDir, "users_slp_wallet.spvchain")
            if(chainFile.exists()) {
                chainFile.delete()
            }
            PrefsUtil.prefs.edit().putBoolean("usingNewBlockStoreSlp", true).apply()
        }

        WalletManager.setupWalletKit(this, null, "", verifyingRestore = false, upgradeToBip47 = false)
        this.displayDownloadContent(true)
        this.displayDownloadContentSlp(true)
        usingBip47CashAccount = PrefsUtil.prefs.getBoolean("bip47CashAcct", false)

        if(!usingBip47CashAccount) {
            upgradeToBip47CashAccount()
        } else {
            val cashAcct = PrefsUtil.prefs.getString("cashAccount", "")!!
            cashAccountSaved = !cashAcct.contains("#???")
            if (!cashAccountSaved) {
                WalletManager.registeredTxHash = PrefsUtil.prefs.getString("cashAcctTx", null).toString()
                val plainName = cashAcct.replace("#???", "")
                NetManager.checkForAccountIdentity(this, plainName, false)

                WalletManager.timer = object : CountDownTimer(150000, 20) {
                    override fun onTick(millisUntilFinished: Long) {

                    }

                    override fun onFinish() {
                        try {
                            NetManager.checkForAccountIdentity(this@MainActivity, plainName, true)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                    }
                }.start()
            }
        }
    }

    private fun findViews() {
        this.createWalletBtn = this.findViewById(R.id.createWalletBtn)
        this.restoreWalletBtn = this.findViewById(R.id.restoreWalletBtn)
        this.restore_wallet = this.findViewById(R.id.restore_wallet)
        this.new_wallet = this.findViewById(R.id.new_wallet)
        this.newuser = this.findViewById(R.id.newuser)
        this.balance = this.findViewById(R.id.balance)
        this.balanceSlp = this.findViewById(R.id.balanceSlp)
        this.registerUserBtn = this.findViewById(R.id.registerUserBtn)
        this.handle = this.findViewById(R.id.handle)
        this.verifyUserBtn = this.findViewById(R.id.verifyUserBtn)
        this.recoverySeed = this.findViewById(R.id.recoverySeed)
        this.handle2 = this.findViewById(R.id.handle2)
        this.fiatBalTxt = this.findViewById(R.id.fiatBalTxt)
        this.fiatBalTxtSlp = this.findViewById(R.id.fiatBalTxtSlp)
        this.syncPct = this.findViewById(R.id.syncPct)
        this.syncPctSlp = this.findViewById(R.id.syncSlp)
        this.btnViewTokens = this.findViewById(R.id.viewSLPBtn)
        this.receiveFabHome = this.findViewById(R.id.receiveFabHome)
        this.receiveSlpHome = this.findViewById(R.id.receiveSlpHome)
        this.sendFabHome = this.findViewById(R.id.sendFabHome)
        this.openKeys = this.findViewById(R.id.openKeys)
        this.btnViewHistory = this.findViewById(R.id.viewBCHBtn)
    }

    private fun prepareViews() {

    }

    private fun initListeners() {
        this.openKeys.setOnClickListener { UIManager.startActivity(this, SettingsActivity::class.java) }
        this.receiveFabHome.setOnClickListener { this.displayReceive() }
        this.receiveSlpHome.setOnClickListener { this.displayReceiveSlp() }
        this.sendFabHome.setOnClickListener { startSendActivity() }
        this.btnViewHistory.setOnClickListener {
            UIManager.startActivity(this, HistoryActivity::class.java)
        }

        this.restoreWalletBtn.setOnClickListener { this.displayRestore() }
        this.createWalletBtn.setOnClickListener { this.displayNewWallet() }
        this.btnViewTokens.setOnClickListener {
            try {
                if (WalletManager.getSlpKit() != null) {
                    val tokensListActivity = Intent(this, TokensListActivity::class.java)
                    this.startActivity(tokensListActivity)
                }
            } catch (e: Exception) {
                UIManager.showToastMessage(this@MainActivity, "SLP Wallet not initialized yet!")
            }
        }

        val permissionHelper = PermissionHelper()
        permissionHelper.askForPermissions(this, this)

        val filter = IntentFilter()
        filter.addAction(Constants.ACTION_UPDATE_HOME_SCREEN_BALANCE)
        filter.addAction(Constants.ACTION_UPDATE_HOME_SCREEN_THEME)
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter)
    }

    private fun startSendActivity() {
        UIManager.startActivity(this, SendActivity::class.java)
    }

    private fun displayNewWallet() {
        this.restore_wallet.visibility = View.GONE
        this.new_wallet.visibility = View.VISIBLE
        this.newuser.visibility = View.GONE
        this.registerUserBtn.setOnClickListener { NetManager.prepareWalletForRegistration(this) }
    }

    private fun displayRestore() {
        isNewUser = false
        this.restore_wallet.visibility = View.VISIBLE
        this.new_wallet.visibility = View.GONE
        this.newuser.visibility = View.GONE
        this.verifyUserBtn.setOnClickListener { NetManager.prepareWalletForVerification(this) }
    }

    @UiThread
    fun displayDownloadContent(status: Boolean) {
        WalletManager.downloading = status

        if (!status) {
            syncPct.text = ""
        }
    }

    @UiThread
    fun displayDownloadContentSlp(status: Boolean) {
        WalletManager.downloadingSlp = status

        if (!status) {
            syncPctSlp.text = ""
        }
    }

    private fun upgradeToBip47CashAccount() {
        val cashAcct = PrefsUtil.prefs.getString("cashAccount", "")!!
        val plainName = cashAcct.split("#")[0]
        val address = WalletManager.walletKit!!.getvWallet().currentReceiveAddress().toString()
        val paymentCode = WalletManager.walletKit!!.paymentCode
        println("Registering...")
        PrefsUtil.prefs.edit().putBoolean("isNewUser", false).apply()
        if (Constants.IS_PRODUCTION) NetManager.registerCashAccount(this, plainName, paymentCode, address)
    }

    private fun displayReceiveSlp() {
        val receiveActivity = Intent(this, ReceiveSLPActivity::class.java)
        this.startActivity(receiveActivity)
    }

    private fun displayReceive() {
        val cashAccount = PrefsUtil.prefs.getString("cashAccount", "")
        val receiveActivity = Intent(this, ReceiveActivity::class.java)
        receiveActivity.putExtra(Constants.INTENT_CASH_ACCOUNT_DATA, cashAccount)
        this.startActivity(receiveActivity)
    }

    fun displayPercentage(percent: Int) {
        if (WalletManager.downloading)
            syncPct.text = "Syncing... $percent%"
    }

    fun displayPercentageSlp(percent: Int) {
        if (WalletManager.downloadingSlp)
            syncPctSlp.text = "Syncing... $percent%"
    }

    fun displayMyBalance(myBalance: String, myBalanceSlp: String?) {
        this.runOnUiThread {
            var balanceStr = myBalance
            balanceStr = balanceStr.replace(" BCH", "")
            var formatted = java.lang.Double.parseDouble(balanceStr)
            var balanceText = ""
            when (WalletManager.displayUnits) {
                MonetaryFormat.CODE_BTC -> {
                    balanceText = UIManager.formatBalance(formatted, "#,###.########")
                }
                MonetaryFormat.CODE_MBTC -> {
                    formatted *= 1000
                    balanceText = UIManager.formatBalance(formatted, "#,###.#####")
                }
                MonetaryFormat.CODE_UBTC -> {
                    formatted *= 1000000
                    balanceText = UIManager.formatBalance(formatted, "#,###.##")
                }
                "sats" -> {
                    formatted *= 100000000
                    balanceText = UIManager.formatBalance(formatted, "#,###")
                }
            }

            var balanceTextSlp = ""
            if (myBalanceSlp != null) {
                var balanceStrSlp = myBalanceSlp
                balanceStrSlp = balanceStrSlp.replace(" BCH", "")
                var formattedSlp = java.lang.Double.parseDouble(balanceStrSlp)

                when (WalletManager.displayUnits) {
                    MonetaryFormat.CODE_BTC -> {
                        balanceTextSlp = UIManager.formatBalance(formattedSlp, "#,###.########")
                    }
                    MonetaryFormat.CODE_MBTC -> {
                        formattedSlp *= 1000
                        balanceTextSlp = UIManager.formatBalance(formattedSlp, "#,###.#####")
                    }
                    MonetaryFormat.CODE_UBTC -> {
                        formattedSlp *= 1000000
                        balanceTextSlp = UIManager.formatBalance(formattedSlp, "#,###.##")
                    }
                    "sats" -> {
                        formattedSlp *= 100000000
                        balanceTextSlp = UIManager.formatBalance(formattedSlp, "#,###")
                    }
                }
            }

            if (UIManager.streetModeEnabled) {
                balance.text = "########"

                if (myBalanceSlp != null) {
                    balanceSlp.text = "########"
                }
            } else {
                balance.text = balanceText

                if (myBalanceSlp != null) {
                    balanceSlp.text = "$balanceTextSlp\n+ ${WalletManager.slpWalletKit!!.slpBalances.size} tokens"
                }
            }
        }
    }

    fun refresh() {
        if (UIManager.showFiat) {
            object : Thread() {
                override fun run() {
                    try {
                        val coinBal = java.lang.Double.parseDouble(WalletManager.getBalance(WalletManager.wallet).toPlainString())
                        val coinBalSlp = java.lang.Double.parseDouble(WalletManager.getSlpWallet().getBalance(Wallet.BalanceType.ESTIMATED).toPlainString())
                        val df = DecimalFormat("#,###.##", DecimalFormatSymbols(Locale.US))

                        val fiatBalances = when (UIManager.fiat) {
                            "USD" -> {
                                val priceUsd = NetManager.price
                                val balUsd = coinBal * priceUsd

                                "$" + df.format(balUsd)
                            }
                            "EUR" -> {
                                val priceEur = NetManager.priceEur
                                val balEur = coinBal * priceEur

                                "€" + df.format(balEur)
                            }
                            "AUD" -> {
                                val priceAud = NetManager.priceAud
                                val balAud = coinBal * priceAud

                                "AUD$" + df.format(balAud)
                            }
                            else -> ""
                        }

                        val fiatBalancesSlp = when (UIManager.fiat) {
                            "USD" -> {
                                val priceUsd = NetManager.price
                                val balUsd = coinBalSlp * priceUsd

                                "$" + df.format(balUsd)
                            }
                            "EUR" -> {
                                val priceEur = NetManager.priceEur
                                val balEur = coinBalSlp * priceEur

                                "€" + df.format(balEur)
                            }
                            "AUD" -> {
                                val priceAud = NetManager.priceAud
                                val balAud = coinBalSlp * priceAud

                                "AUD$" + df.format(balAud)
                            }
                            else -> ""
                        }

                        this@MainActivity.runOnUiThread {
                            if (UIManager.streetModeEnabled) {
                                fiatBalTxt.text = "####"
                                fiatBalTxtSlp.text = "####"
                            } else {
                                fiatBalTxt.text = fiatBalances
                                fiatBalTxtSlp.text = fiatBalancesSlp
                            }
                        }
                    } catch (e: Exception) {
                        //fail silently
                    }
                }
            }.start()
        } else {
            fiatBalTxt.text = ""
            fiatBalTxtSlp.text = ""
        }

        try {
            displayMyBalance(WalletManager.getBalance(WalletManager.wallet).toFriendlyString(), WalletManager.getSlpWallet().getBalance(Wallet.BalanceType.ESTIMATED).toFriendlyString())
        } catch (e: Exception) {
            //fail silently
        }
    }

    override fun onBackPressed() {
        when {
            this.new_wallet.visibility == View.VISIBLE -> {
                this.new_wallet.visibility = View.GONE
                this.newuser.visibility = View.VISIBLE
            }
            this.restore_wallet.visibility == View.VISIBLE -> {
                this.restore_wallet.visibility = View.GONE
                this.newuser.visibility = View.VISIBLE
            }
            else -> {
                val intent = Intent(Intent.ACTION_MAIN)
                intent.addCategory(Intent.CATEGORY_HOME)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == Constants.REQUEST_CODE_DECRYPT) {
            this.startWalletAndChecks()
        }
    }

    /*
    Read the comment within the method. This is needed so the proper Bouncycastle is loaded, so we can use the Elliptic Curve Diffie-Hellman algorithm for BIP47 common secret calculation.
     */
    private fun setupBouncyCastle() {
        val provider: Provider = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)
                ?:
                return
        if (provider.javaClass == BouncyCastleProvider::class.java) { // BC with same package name, shouldn't happen in real life.
            return
        }
        // Android registers its own BC provider. As it might be outdated and might not include
// all needed ciphers, we substitute it with a known BC bundled in the app.
// Android's BC has its package rewritten to "com.android.org.bouncycastle" and because
// of that it's possible to have another BC implementation loaded in VM.
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }

    companion object {
        var isNewUser = true
        var cashAccountSaved = false
        var usingBip47CashAccount = false
        var usingNewBlockStore = false
        var usingNewBlockStoreSlp = false
        lateinit var dataDirectory: String

        fun getDataDir(): String {
            return dataDirectory
        }
    }
}
