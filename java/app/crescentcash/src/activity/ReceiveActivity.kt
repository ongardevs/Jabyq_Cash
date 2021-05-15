package app.crescentcash.src.activity

import android.content.*
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import app.crescentcash.src.MainActivity
import app.crescentcash.src.R
import app.crescentcash.src.hash.HashHelper
import app.crescentcash.src.manager.NetManager
import app.crescentcash.src.manager.UIManager
import app.crescentcash.src.manager.WalletManager
import app.crescentcash.src.utils.Constants
import app.crescentcash.src.utils.PrefsUtil
import com.google.zxing.BarcodeFormat
import com.google.zxing.WriterException
import com.journeyapps.barcodescanner.BarcodeEncoder


class ReceiveActivity : AppCompatActivity() {
    private lateinit var btcAddress: TextView
    private lateinit var myCashHandle: TextView
    private lateinit var srlContent_AM: SwipeRefreshLayout
    private lateinit var toggleAddr: ImageButton
    private var currentAddrView = true
    private var cashAccount: String? = ""
    private var receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (Constants.ACTION_UPDATE_CASH_ACCOUNT_LABEL == intent.action) {
                this@ReceiveActivity.cashAccount = PrefsUtil.prefs.getString("cashAccount", "")
                this@ReceiveActivity.displayCashAccount()
                this@ReceiveActivity.calculateEmoji()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIManager.determineTheme(this)
        this.setContentView(R.layout.receive)
        this.findViews()
        this.prepareViews(savedInstanceState)
        this.initListeners()
    }

    private fun prepareViews(savedInstanceState: Bundle?) {
        cashAccount = if (savedInstanceState == null) {
            val extras = intent.extras
            if (extras == null) {
                ""
            } else {
                extras.getString(Constants.INTENT_CASH_ACCOUNT_DATA)
            }
        } else {
            savedInstanceState.getString(Constants.INTENT_CASH_ACCOUNT_DATA)
        }

        this.displayCashAccount()
        this.calculateEmoji()
    }

    private fun findViews() {
        btcAddress = this.findViewById(R.id.btcAddress)
        myCashHandle = this.findViewById(R.id.myCashHandle)
        srlContent_AM = this.findViewById(R.id.srlContent_AM)
        toggleAddr = this.findViewById(R.id.toggleAddr)
    }

    private fun initListeners() {
        this.srlContent_AM.setOnRefreshListener {
            this.displayCashAccount()
            this.calculateEmoji()

            if (this.srlContent_AM.isRefreshing) this.srlContent_AM.isRefreshing = false
        }

        val copyListener = View.OnClickListener { copyAddr() }

        this.findViewById<ImageView>(R.id.btcQR).setOnClickListener(copyListener)
        this.btcAddress.setOnClickListener { this.copyAddr() }
        this.myCashHandle.setOnClickListener { this.copyCashAcct() }

        this.toggleAddr.setOnClickListener {
            this.currentAddrView = !this.currentAddrView
            this.displayCashAccount()
        }

        val filter = IntentFilter()
        filter.addAction(Constants.ACTION_UPDATE_CASH_ACCOUNT_LABEL)
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter)
    }

    private fun copyAddr() {
        val myAddress = if(currentAddrView) {
            btcAddress.text.toString()
        } else {
            WalletManager.parameters.cashAddrPrefix + ":" + btcAddress.text.toString()
        }
        val clip: ClipData = ClipData.newPlainText("My BCH address", myAddress)
        val clipboard = this.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(clip)
        UIManager.showToastMessage(this, "Copied")
    }

    private fun copyCashAcct() {
        val cashEmoji = PrefsUtil.prefs.getString("cashEmoji", "")
        val cashAcct = "${myCashHandle.text.substring(2).trim()}; $cashEmoji"
        val cashAcctString = "$cashAcct"
        val clip: ClipData = ClipData.newPlainText("My Cash Account", cashAcctString)
        val clipboard = this.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(clip)
        UIManager.showToastMessage(this, "Copied")
    }

    fun displayCashAccount() {
        val cashEmoji = PrefsUtil.prefs.getString("cashEmoji", "")
        this.displayCashAccount(cashEmoji!!)
    }

    fun displayCashAccount(emoji: String) {
        myCashHandle.text = "$emoji $cashAccount"

        if (!MainActivity.cashAccountSaved) {
            println("CASH ACCOUNT UNCONFIRMED")
            println("CASH HANDLE " + myCashHandle.text.toString())
            val cashAcctPlain = myCashHandle.text.toString().replace("#???", "").substring(2).trim()
            println(cashAcctPlain)
            NetManager.checkForAccountIdentity(this, cashAcctPlain, false)
        }

        if (WalletManager.walletKit != null) {
            if(currentAddrView) {
                this.btcAddress.text = WalletManager.walletKit?.paymentCode
            } else {
                this.btcAddress.text = WalletManager.wallet.currentReceiveAddress().toString().replace(WalletManager.parameters.cashAddrPrefix + ":", "")
            }

            generateQR(this.btcAddress.text.toString(), R.id.btcQR, false)
        } else {
            this.btcAddress.text = "Loading..."
        }
    }

    private fun generateQR(textToConvert: String, viewID: Int, slp: Boolean) {
        try {
            val encoder = BarcodeEncoder()

            val qrCode = encoder.encodeBitmap(textToConvert, BarcodeFormat.QR_CODE, 1024, 1024)

            val coinLogo: Bitmap? = if (!slp)
                drawableToBitmap(this.resources.getDrawable(R.drawable.logo_bch))
            else
                drawableToBitmap(this.resources.getDrawable(R.drawable.logo_slp))

            val merge = overlayBitmapToCenter(qrCode, coinLogo!!)
            (this.findViewById<View>(viewID) as ImageView).setImageBitmap(merge)
        } catch (e: WriterException) {
            e.printStackTrace()
        }
    }

    /*
    I'm absolutely terrible with Bitmap and image generation shit. Always have been.

    Shout-out to StackOverflow for some of this.
     */
    private fun overlayBitmapToCenter(bitmap1: Bitmap, bitmap2: Bitmap): Bitmap {
        val bitmap1Width = bitmap1.width
        val bitmap1Height = bitmap1.height
        val bitmap2Width = bitmap2.width
        val bitmap2Height = bitmap2.height

        val marginLeft = (bitmap1Width * 0.5 - bitmap2Width * 0.5).toFloat()
        val marginTop = (bitmap1Height * 0.5 - bitmap2Height * 0.5).toFloat()

        val overlayBitmap = Bitmap.createBitmap(bitmap1Width, bitmap1Height, bitmap1.config)
        val canvas = Canvas(overlayBitmap)
        canvas.drawBitmap(bitmap1, Matrix(), null)
        canvas.drawBitmap(bitmap2, marginLeft, marginTop, null)
        return overlayBitmap
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap? {
        val bitmap: Bitmap? = if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888) // Single color bitmap will be created of 1x1 pixel
        } else {
            Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        }

        if (drawable is BitmapDrawable) {
            if (drawable.bitmap != null) {
                return drawable.bitmap
            }
        }

        val canvas = Canvas(bitmap!!)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun calculateEmoji() {
        val savedEmoji = PrefsUtil.prefs.getString("cashEmoji", "")
        if (savedEmoji == "?" || savedEmoji == "") {
            object : Thread() {
                override fun run() {
                    if (WalletManager.registeredTxHash != null) {
                        try {
                            if(WalletManager.registeredBlockHash != null) {
                                if (WalletManager.registeredBlock != null) {
                                    try {
                                        val emoji = HashHelper().getCashAccountEmoji(WalletManager.registeredBlockHash!!, WalletManager.registeredTxHash!!)
                                        PrefsUtil.prefs.edit().putString("cashEmoji", emoji).apply()
                                        this@ReceiveActivity.displayCashAccount(emoji)
                                    } catch (e: Exception) {
                                        this@ReceiveActivity.displayCashAccount("?")
                                    }

                                } else {
                                    this@ReceiveActivity.displayCashAccount("?")
                                }
                            } else {
                                this@ReceiveActivity.displayCashAccount("?")
                            }
                        } catch (e: NullPointerException) {
                            e.printStackTrace()
                            this@ReceiveActivity.displayCashAccount("?")
                        }
                    } else {
                        try {
                            val cashAcctName = PrefsUtil.prefs.getString("cashAccount", "") as String
                            val cashAcctEmoji = NetManager.getCashAccountEmoji(cashAcctName)
                            PrefsUtil.prefs.edit().putString("cashEmoji", cashAcctEmoji).apply()
                            this@ReceiveActivity.displayCashAccount()
                        } catch (e: Exception) {
                            e.printStackTrace()
                            runOnUiThread { UIManager.showToastMessage(this@ReceiveActivity, "Error getting emoji") }
                        }

                    }
                }
            }.start()
        } else {
            this@ReceiveActivity.displayCashAccount(savedEmoji!!)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(Constants.INTENT_CASH_ACCOUNT_DATA, myCashHandle.text.toString())
    }

    override fun onBackPressed() {
        super.onBackPressed()
        this.finish()
    }

    companion object
}
