package app.crescentcash.src.activity

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import app.crescentcash.src.R
import app.crescentcash.src.manager.UIManager
import app.crescentcash.src.manager.WalletManager
import com.google.zxing.BarcodeFormat
import com.google.zxing.WriterException
import com.journeyapps.barcodescanner.BarcodeEncoder

class ReceiveSLPActivity : AppCompatActivity() {
    private lateinit var slpAddress: TextView
    private var currentAddrView = true
    private lateinit var toggleAddr: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIManager.determineTheme(this)
        this.setContentView(R.layout.receive_slp)
        this.findViews()
        this.prepareViews()
        this.initListeners()
    }

    private fun prepareViews() {
        this.displayReceiveSLP()
    }

    private fun findViews() {
        slpAddress = this.findViewById(R.id.slpAddress)
        toggleAddr = this.findViewById(R.id.toggleAddr)
    }

    private fun initListeners() {
        val copyListener = View.OnClickListener { copyAddr() }
        this.findViewById<ImageView>(R.id.slpQR).setOnClickListener(copyListener)
        this.toggleAddr.setOnClickListener {
            this.currentAddrView = !this.currentAddrView
            this.displayReceiveSLP()
        }
    }

    private fun copyAddr() {
        val clip: ClipData = if (currentAddrView) ClipData.newPlainText("My SLP address", "simpleledger:" + slpAddress.text.toString()) else ClipData.newPlainText("My BCH SLP address", "bitcoincash:" + slpAddress.text.toString())
        val clipboard = this.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
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

    private fun displayReceiveSLP() {
        if (WalletManager.walletKit != null) {
            val address = if (currentAddrView)
                WalletManager.getSlpKit().currentSlpReceiveAddress().toString()
            else
                WalletManager.getSlpKit().currentSlpReceiveAddress().toCashAddress()

            slpAddress.text = address.replace("simpleledger:", "").replace(WalletManager.parameters.cashAddrPrefix + ":", "")

            if (currentAddrView)
                generateQR(address, R.id.slpQR, true)
            else
                generateQR(address, R.id.slpQR, false)
        } else {
            slpAddress.text = "Loading..."
        }
    }

    companion object
}
