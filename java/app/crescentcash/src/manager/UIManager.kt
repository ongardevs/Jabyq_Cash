package app.crescentcash.src.manager

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import app.crescentcash.src.R
import app.crescentcash.src.qr.QRHelper
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*

class UIManager {
    companion object {
        var fiat = "USD"
        val isDisplayingDownload: Boolean
            get() = WalletManager.downloading
        var emojis = intArrayOf(128123, 128018, 128021, 128008, 128014, 128004, 128022, 128016, 128042, 128024, 128000, 128007, 128063, 129415, 128019, 128039, 129414, 129417, 128034, 128013, 128031, 128025, 128012, 129419, 128029, 128030, 128375, 127803, 127794, 127796, 127797, 127809, 127808, 127815, 127817, 127819, 127820, 127822, 127826, 127827, 129373, 129381, 129365, 127805, 127798, 127812, 129472, 129370, 129408, 127850, 127874, 127853, 127968, 128663, 128690, 9973, 9992, 128641, 128640, 8986, 9728, 11088, 127752, 9730, 127880, 127872, 9917, 9824, 9829, 9830, 9827, 128083, 128081, 127913, 128276, 127925, 127908, 127911, 127928, 127930, 129345, 128269, 128367, 128161, 128214, 9993, 128230, 9999, 128188, 128203, 9986, 128273, 128274, 128296, 128295, 9878, 9775, 128681, 128099, 127838)
        var nightModeEnabled = false
        var showFiat = true
        var streetModeEnabled = false

        fun formatBalance(amount: Double, pattern: String): String {
            val formatter = DecimalFormat(pattern, DecimalFormatSymbols(Locale.US))
            val formattedStr = formatter.format(amount)
            return "$formattedStr ${WalletManager.displayUnits}"
        }

        fun formatBalanceNoUnit(amount: Double, pattern: String): String {
            val formatter = DecimalFormat(pattern, DecimalFormatSymbols(Locale.US))
            val formattedStr = formatter.format(amount)
            return "$formattedStr"
        }

        fun showAlertDialog(context: Context, title: String, message: String, closePrompt: String) {
            val builder = androidx.appcompat.app.AlertDialog.Builder(context, if (this.nightModeEnabled) R.style.AlertDialogDark else R.style.AlertDialogLight)
            builder.setTitle(title)
            builder.setMessage(message)
            builder.setCancelable(true)
            builder.setPositiveButton(closePrompt) { dialog, which -> dialog.dismiss() }
            val alertDialog = builder.create()
            alertDialog.show()
            val msgTxt = alertDialog.findViewById<View>(android.R.id.message) as TextView
            msgTxt.movementMethod = LinkMovementMethod.getInstance()
        }

        fun determineTheme(activity: Activity) {
            activity.setTheme(if (this.nightModeEnabled) R.style.CrescentCashDark else R.style.CrescentCashLight)

            val decorView = activity.window.decorView
            val window = activity.window
            if (this.nightModeEnabled) {
                var flags = decorView.systemUiVisibility
                flags = flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    flags = flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                }
                decorView.systemUiVisibility = flags
                activity.window.statusBarColor = activity.resources.getColor(R.color.statusBarDark)
                window.navigationBarColor = activity.resources.getColor(R.color.navBarDark)
            } else {
                var flags = decorView.systemUiVisibility
                flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                }
                decorView.systemUiVisibility = flags
                activity.window.statusBarColor = activity.resources.getColor(R.color.statusBarLight)
                window.navigationBarColor = activity.resources.getColor(R.color.navBarLight)
            }
        }

        fun showToastMessage(context: Context, message: String) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }

        fun clickScanQR(activity: AppCompatActivity, requestCode: Int) {
            val qrHelper = QRHelper()
            qrHelper.startQRScan(activity, requestCode)
        }

        fun startActivity(context: Activity, activity: Class<*>) {
            val activityToStart = Intent(context, activity)
            context.startActivity(activityToStart)
        }

        fun playAudio(activity: Activity, audioFile: Int) {
            val mediaPlayer = MediaPlayer.create(activity, audioFile)
            mediaPlayer.start()
        }
    }
}