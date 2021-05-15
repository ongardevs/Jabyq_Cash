package app.crescentcash.src.utils

import android.content.SharedPreferences
import app.crescentcash.src.manager.UIManager
import app.crescentcash.src.manager.WalletManager
import org.bitcoinj.utils.MonetaryFormat

class PrefsUtil {
    companion object {
        lateinit var prefs: SharedPreferences

        fun loadPrefs() {
            UIManager.nightModeEnabled = prefs.getBoolean("nightMode", true)
            UIManager.streetModeEnabled = prefs.getBoolean("streetMode", false)
            UIManager.showFiat = prefs.getBoolean("showFiat", true)
            UIManager.fiat = prefs.getString("fiat", "USD") as String
            WalletManager.displayUnits = prefs.getString("displayUnit", MonetaryFormat.CODE_BTC) as String
            WalletManager.sendType = prefs.getString("sendType", WalletManager.displayUnits) as String
            WalletManager.addOpReturn = prefs.getBoolean("addOpReturn", false)
            WalletManager.encrypted = prefs.getBoolean("useEncryption", false)
            WalletManager.useTor = prefs.getBoolean("useTor", false)
            WalletManager.allowLegacyP2SH = prefs.getBoolean("allowLegacyP2SH", false)
            WalletManager.maximumAutomaticSend = prefs.getFloat("maximumAutomaticSend", 0.00f)
        }
    }
}