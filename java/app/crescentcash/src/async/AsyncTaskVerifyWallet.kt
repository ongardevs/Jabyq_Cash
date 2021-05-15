package app.crescentcash.src.async

import android.os.AsyncTask
import android.view.View
import app.crescentcash.src.MainActivity
import app.crescentcash.src.manager.NetManager
import app.crescentcash.src.manager.UIManager
import app.crescentcash.src.manager.WalletManager
import app.crescentcash.src.utils.PrefsUtil
import org.bitcoinj.core.Address
import org.bitcoinj.core.CashAddress
import org.bitcoinj.core.LegacyAddress
import org.bitcoinj.core.bip47.BIP47PaymentCode
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.Wallet

class AsyncTaskVerifyWallet(val activity: MainActivity, val cashAcctName: String, val seed: DeterministicSeed) : AsyncTask<Void, Void, String>() {
    lateinit var cashAcctEmoji: String

    override fun doInBackground(vararg params: Void?): String? {
        println("VERIFYING CASH ACCOUNT")
        return try {
            val cashAcctAddress = org.bitcoinj.net.NetHelper().getCashAccountAddress(WalletManager.parameters, cashAcctName)
            cashAcctEmoji = NetManager.getCashAccountEmoji(cashAcctName)
            cashAcctAddress
        } catch (e: NullPointerException) {
            println("ERROR")
            e.printStackTrace()
            activity.runOnUiThread { UIManager.showToastMessage(activity, "Cash Account not found.") }
            null
        }
    }

    override fun onPostExecute(result: String?) {
        super.onPostExecute(result)
        println(result)

        if(result != null) {
            if (Address.isValidPaymentCode(result)) {
                MainActivity.usingBip47CashAccount = true
                WalletManager.setupWalletKit(activity, seed, cashAcctName, verifyingRestore = true, upgradeToBip47 = false)

                val pref = PrefsUtil.prefs.edit()
                pref.putString("cashAccount", cashAcctName)
                pref.putString("cashEmoji", cashAcctEmoji)
                pref.putBoolean("isNewUser", false)
                pref.putBoolean("bip47CashAcct", MainActivity.usingBip47CashAccount)
                pref.apply()

                activity.runOnUiThread {
                    activity.restore_wallet.visibility = View.GONE
                    activity.displayDownloadContent(true)
                    activity.displayDownloadContentSlp(true)
                    activity.runOnUiThread { UIManager.showToastMessage(activity, "Verified!") }
                }
            } else {
                val accountAddress: Address? = when {
                    Address.isValidCashAddr(WalletManager.parameters, result) -> CashAddress.fromCashAddress(WalletManager.parameters, result)
                    Address.isValidLegacyAddress(WalletManager.parameters, result) -> LegacyAddress.fromBase58(WalletManager.parameters, result)
                    else -> {
                        activity.runOnUiThread { UIManager.showToastMessage(activity, "No address found!") }
                        null
                    }
                }

                val tempWallet = Wallet.fromSeed(WalletManager.parameters, seed)

                val isAddressMine = if (accountAddress != null) {
                    tempWallet.isPubKeyHashMine(accountAddress.hash)
                } else {
                    false
                }

                if (isAddressMine) {
                    WalletManager.setupWalletKit(activity, seed, cashAcctName, verifyingRestore = true, upgradeToBip47 = true)

                    val pref = PrefsUtil.prefs.edit()
                    pref.putString("cashAccount", cashAcctName)
                    pref.putString("cashEmoji", cashAcctEmoji)
                    pref.putBoolean("isNewUser", false)
                    pref.putBoolean("bip47CashAcct", false)
                    pref.apply()

                    activity.runOnUiThread {
                        activity.restore_wallet.visibility = View.GONE
                        activity.displayDownloadContent(true)
                        activity.displayDownloadContentSlp(true)
                        activity.runOnUiThread { UIManager.showToastMessage(activity, "Verified!") }
                    }
                } else {
                    activity.runOnUiThread { UIManager.showToastMessage(activity, "Verification failed!") }
                }
            }
        } else {
            activity.runOnUiThread { UIManager.showToastMessage(activity, "Verification failed, try again.") }
        }
    }
}