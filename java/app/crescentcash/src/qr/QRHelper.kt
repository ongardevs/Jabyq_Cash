package app.crescentcash.src.qr

import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.integration.android.IntentIntegrator
import com.journeyapps.barcodescanner.CaptureActivity

class QRHelper {
    fun startQRScan(activity: AppCompatActivity, requestCode: Int) {
        IntentIntegrator(activity).setPrompt("Scan QR").setBeepEnabled(false).setDesiredBarcodeFormats(BarcodeFormat.QR_CODE.name).setOrientationLocked(true).setCameraId(0).setCaptureActivity(CaptureActivity::class.java).setRequestCode(requestCode).initiateScan()
    }
}
