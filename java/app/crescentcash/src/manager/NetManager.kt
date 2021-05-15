package app.crescentcash.src.manager

import android.app.Activity
import android.content.Intent
import android.os.CountDownTimer
import android.text.TextUtils
import android.view.View
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import app.crescentcash.src.MainActivity
import app.crescentcash.src.activity.SendActivity
import app.crescentcash.src.async.AsyncTaskVerifyWallet
import app.crescentcash.src.hash.HashHelper
import app.crescentcash.src.json.JSONHelper
import app.crescentcash.src.utils.Constants
import app.crescentcash.src.utils.PrefsUtil
import com.google.common.base.Splitter
import org.bitcoinj.core.ECKey
import org.bitcoinj.crypto.MnemonicCode
import org.bitcoinj.crypto.MnemonicException
import org.bitcoinj.wallet.DeterministicSeed
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.FileNotFoundException
import java.io.InputStreamReader
import java.net.*
import java.security.SecureRandom
import java.util.*

class NetManager {

    companion object {
        var torProxy: Proxy? = null

        fun establishProxy() {
            if (WalletManager.useTor) {
                torProxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 9050))
            }
        }

        fun prepareWalletForRegistration(activity: MainActivity) {
            val cashAcctName = activity.handle.text.toString()

            if (!TextUtils.isEmpty(cashAcctName)) {
                if (!cashAcctName.contains("#") && !cashAcctName.contains(".")) {
                    val entropy = entropy
                    var mnemonic: List<String>? = null
                    try {
                        mnemonic = MnemonicCode.INSTANCE.toMnemonic(entropy)
                    } catch (e: MnemonicException.MnemonicLengthException) {
                        e.printStackTrace()
                    }

                    val mnemonicCode = mnemonic
                    val recoverySeed = StringBuilder()

                    assert(mnemonicCode != null)
                    for (x in mnemonicCode!!.indices) {
                        recoverySeed.append(mnemonicCode[x]).append(if (x == mnemonicCode.size - 1) "" else " ")
                    }

                    val seedStr = recoverySeed.toString()
                    val seed = DeterministicSeed(Splitter.on(' ').splitToList(seedStr), null, "", System.currentTimeMillis() / 1000L)
                    val length = Splitter.on(' ').splitToList(seedStr).size

                    if (length == 12) {
                        WalletManager.setupWalletKit(activity, seed, activity.handle.text.toString(), verifyingRestore = false, upgradeToBip47 = false)
                        activity.displayDownloadContent(true)
                        activity.displayDownloadContentSlp(true)
                        activity.new_wallet.visibility = View.GONE
                        UIManager.showToastMessage(activity, "Registering user...")

                        WalletManager.timer = object : CountDownTimer(150000, 20) {
                            override fun onTick(millisUntilFinished: Long) {

                            }

                            override fun onFinish() {
                                try {
                                    checkForAccountIdentity(activity, activity.handle.text.toString(), true)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }

                            }
                        }.start()
                    }
                } else {
                    UIManager.showToastMessage(activity, "Do not include identifier!")
                }
            }
        }

        fun registerCashAccount(activity: MainActivity, cashAcctName: String, paymentCode: String, address: String) {
            object : Thread() {
                override fun run() {
                    if (!cashAcctName.contains("#")) {
                        val json = JSONObject()

                        try {
                            json.put("name", cashAcctName)

                            val paymentsArray = JSONArray()
                            paymentsArray.put(paymentCode)
                            paymentsArray.put(address)

                            json.put("payments", paymentsArray)

                        } catch (e: JSONException) {
                            e.printStackTrace()
                        }

                        val requestUrl = "https://api.cashaccount.info/register/"
                        var url: URL? = null
                        try {
                            url = URL(requestUrl)
                        } catch (e: MalformedURLException) {
                            e.printStackTrace()
                        }

                        var connection: HttpURLConnection? = null

                        try {
                            if (url != null) {
                                connection = if (WalletManager.useTor)
                                    url.openConnection(torProxy) as HttpURLConnection
                                else
                                    url.openConnection() as HttpURLConnection
                            }
                            if (connection != null) {
                                connection.doOutput = true
                                connection.doInput = true
                                connection.instanceFollowRedirects = false
                                connection.requestMethod = "POST"
                                connection.setRequestProperty("Content-Type", "application/json")
                                connection.setRequestProperty("charset", "utf-8")
                                connection.setRequestProperty("Accept", "application/json")
                                connection.setRequestProperty("Content-Length", json.toString().toByteArray().size.toString())
                                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; rv:60.0) Gecko/20100101 Firefox/60.0")

                                connection.useCaches = false

                                connection.connectTimeout = 60000
                                connection.readTimeout = 60000

                                connection.connect()

                                val wr = DataOutputStream(connection.outputStream)
                                wr.write(json.toString().toByteArray())
                                wr.flush()
                                wr.close()

                                val rd = BufferedReader(InputStreamReader(connection.inputStream))
                                val res = StringBuilder()
                                while (true) {
                                    val line = rd.readLine()

                                    if (line != null)
                                        res.append(line)
                                    else
                                        break
                                }

                                wr.flush()
                                wr.close()

                                val responseJson = res.toString()
                                println(responseJson)

                                val jsonHelper = JSONHelper()

                                WalletManager.registeredTxHash = jsonHelper.getRegisterTxHash(responseJson)
                                MainActivity.usingBip47CashAccount = true
                                val editor = PrefsUtil.prefs.edit()
                                editor.putString("cashAccount", "$cashAcctName#???")
                                editor.putString("cashEmoji", "?")
                                editor.putString("cashAcctTx", WalletManager.registeredTxHash)
                                editor.putBoolean("bip47CashAcct", MainActivity.usingBip47CashAccount)
                                editor.apply()
                                activity.runOnUiThread {
                                    UIManager.showAlertDialog(activity, "WARNING!", "Be sure to write down your recovery seed and save your Cash Account name and identifier when confirmed! You will need it to restore the wallet in this app!", "Got it")
                                    UIManager.showToastMessage(activity, "Registered!")
                                    activity.refresh()
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    } else {
                        activity.runOnUiThread { UIManager.showToastMessage(activity, "Do not set an account number!") }
                    }
                }
            }.start()
        }

        fun prepareWalletForVerification(activity: MainActivity) {
            val cashAcctName = activity.handle2.text.toString().trim()

            if (!TextUtils.isEmpty(cashAcctName)) {
                if (cashAcctName.contains("#")) {
                    val seedStr = activity.recoverySeed.text.toString().trim()
                    val creationTime = 1560281760L
                    val seed = DeterministicSeed(Splitter.on(' ').splitToList(seedStr), null, "", creationTime)
                    val length = Splitter.on(' ').splitToList(seedStr).size

                    if (length == 12) {
                        UIManager.showToastMessage(activity, "Verifying user...")

                        val task = AsyncTaskVerifyWallet(activity, cashAcctName, seed)
                        task.execute()
                    }
                } else {
                    UIManager.showToastMessage(activity, "Please include the identifier!")
                }
            }
        }

        fun getCashAccountEmoji(cashAccount: String): String {
            val randExplorer = Random().nextInt(cashAcctServers.size)
            val lookupServer = cashAcctServers[randExplorer]
            println(lookupServer)
            var emoji = ""

            val splitAccount = cashAccount.split("#".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val name = splitAccount[0]
            val block = splitAccount[1]
            val urlString: String

            if (!lookupServer.contains("rest.bitcoin.com")) {
                urlString = if (!block.contains(".")) {
                    "$lookupServer/account/$block/$name"
                } else {
                    val splitBlock = block.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    val mainBlock = splitBlock[0]
                    val blockCollision = splitBlock[1]
                    "$lookupServer/account/$mainBlock/$name/$blockCollision"
                }
            } else {
                urlString = if (!block.contains(".")) {
                    "$lookupServer/lookup/$name/$block"
                } else {
                    val splitBlock = block.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    val mainBlock = splitBlock[0]
                    val blockCollision = splitBlock[1]
                    "$lookupServer/lookup/$name/$mainBlock/$blockCollision"
                }
            }

            val json = JSONHelper().getJsonObject(urlString)

            emoji = if (json != null)
                json.getJSONObject("information").getString("emoji")
            else
                "?"

            return emoji
        }

        fun checkForAccountIdentity(activity: Activity, name: String, timer: Boolean) {
            object : Thread() {
                override fun run() {
                    if(WalletManager.registeredTxHash != null) {
                        val hashHelper = HashHelper()
                        val registeredBlock = getTransactionData(WalletManager.registeredTxHash!!, "block_height", "blockheight")
                        if(registeredBlock != null) {
                            WalletManager.registeredBlock = registeredBlock
                            val registeredBlockHash = getTransactionData(WalletManager.registeredTxHash!!, "block_hash", "blockhash")

                            if(registeredBlockHash != null) {
                                WalletManager.registeredBlockHash = registeredBlockHash
                                val accountIdentity = Integer.parseInt(registeredBlock) - Constants.CASH_ACCOUNT_GENESIS_MODIFIED
                                val collisionIdentifier = hashHelper.getCashAccountCollision(registeredBlockHash, WalletManager.registeredTxHash!!)
                                val identifier = getCashAccountIdentifier("$name#$accountIdentity.$collisionIdentifier")

                                if(identifier != null) {
                                    val emoji = hashHelper.getCashAccountEmoji(registeredBlockHash, WalletManager.registeredTxHash!!)
                                    PrefsUtil.prefs.edit().putString("cashAccount", identifier).apply()
                                    PrefsUtil.prefs.edit().putString("cashEmoji", emoji).apply()
                                    val intent = Intent(Constants.ACTION_UPDATE_CASH_ACCOUNT_LABEL)
                                    LocalBroadcastManager.getInstance(activity).sendBroadcast(intent)
                                    MainActivity.cashAccountSaved = true
                                } else {
                                    if(timer)
                                        WalletManager.timer.start()
                                }
                            } else {
                                println("Block not found... checking in 2.5 minutes.")
                                if(timer)
                                    WalletManager.timer.start()
                            }
                        } else {
                            println("Block not found... checking in 2.5 minutes.")
                            if(timer)
                                WalletManager.timer.start()
                        }
                    } else {
                        println("Block not found... checking in 2.5 minutes.")
                        if(timer)
                            WalletManager.timer.start()
                    }
                }
            }.start()
        }

        private fun getCashAccountIdentifier(cashAccount: String): String? {
            val randExplorer = Random().nextInt(cashAcctServers.size)
            val lookupServer = cashAcctServers[randExplorer]
            val identity: String?
            val splitAccount = cashAccount.split("#".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val name = splitAccount[0]
            val block = splitAccount[1]
            val urlString: String

            if (!lookupServer.contains("rest.bitcoin.com")) {
                urlString = if (block.contains(".")) {
                    val splitBlock = block.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    val mainBlock = splitBlock[0]
                    val blockCollision = splitBlock[1]
                    "$lookupServer/account/$mainBlock/$name/$blockCollision"
                } else {
                    "$lookupServer/account/$block/$name"
                }
            } else {
                urlString = if (block.contains(".")) {
                    val splitBlock = block.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    val mainBlock = splitBlock[0]
                    val blockCollision = splitBlock[1]
                    "$lookupServer/lookup/$name/$mainBlock/$blockCollision"
                } else {
                    "$lookupServer/lookup/$name/$block"
                }
            }

            val json = JSONHelper().getJsonObject(urlString)

            identity = json?.getString("identifier")?.replace(";", "")

            return identity
        }

        fun getTransactionData(transactionHash: String, variable_one: String, variable_two: String): String? {
            val randExplorer = Random().nextInt(blockExplorers.size)
            val blockExplorer = blockExplorers[randExplorer]
            val blockExplorerURL = blockExplorerAPIURL[randExplorer]

            val txHash = transactionHash.toLowerCase(Locale.US)
            val block = getVariable(blockExplorerURL + txHash, blockExplorer, variable_one, variable_two)

            return if (block == "-1") null else block
        }

        private fun getVariable(url: String, blockExplorer: String, variable_one: String, variable_two: String): String {
            val json: JSONObject? = JSONHelper().getJsonObject(url)

            if (json != null) {
                if (blockExplorer == "rest.bitcoin.com") {
                    return try {
                        if(variable_two == "blockheight")
                            json.getInt(variable_two).toString()
                        else
                            json.getString(variable_two)
                    } catch (e: JSONException) {
                        "-1"
                    }
                }
            } else
                return "-1"

            return "-1"
        }

        fun broadcastTransaction(sendActivity: SendActivity?, hex: String, baseUrl: String) {
            object : Thread() {
                override fun run() {
                    try {
                        val url = URL("$baseUrl/$hex")

                        with(if (WalletManager.useTor) url.openConnection(torProxy) as HttpURLConnection else url.openConnection() as HttpURLConnection) {
                            requestMethod = "GET"

                            try {
                                if (responseCode == 200) {
                                    val sb = StringBuilder()
                                    while (true) {
                                        val cp = inputStream.bufferedReader().read()

                                        if (cp != -1)
                                            sb.append(cp.toChar())
                                        else
                                            break
                                    }

                                    println(sb.toString())
                                }
                            } catch (e: Exception) {
                                if (sendActivity != null)
                                    WalletManager.throwSendError(sendActivity, "Failed to broadcast transaction.")

                                e.printStackTrace()
                            }
                        }
                    } catch (e: FileNotFoundException) {
                        if (sendActivity != null)
                            WalletManager.throwSendError(sendActivity, "Failed to broadcast transaction.")

                        e.printStackTrace()
                    }
                }
            }.start()
        }

        fun checkForCashAccount(ecKey: ECKey, txHash: String, name: String): String {
            try {
                val blockHeight = getTransactionData(txHash, "block_height", "blockheight")

                if (blockHeight == null) {
                    return "$name#???"
                } else if (blockHeight != "") {
                    val blockHash = getTransactionData(txHash, "block_hash", "blockhash")

                    if(blockHash != null) {
                        val accountIdentity = Integer.parseInt(blockHeight) - Constants.CASH_ACCOUNT_GENESIS_MODIFIED
                        val hashHelper = HashHelper()
                        val collisionIdentifier = hashHelper.getCashAccountCollision(blockHash, txHash)
                        val identifier = this.getCashAccountIdentifier("$name#$accountIdentity.$collisionIdentifier")

                        return if (identifier != null) {
                            PrefsUtil.prefs.edit().putString("cashacct_${ecKey.toAddress(WalletManager.parameters)}", identifier).apply()
                            identifier
                        } else {
                            "$name#???"
                        }
                    } else {
                        return "$name#???"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return "$name#???"
            }

            return "$name#???"
        }

        fun reverseLookupCashAccount(cashAddr: String, legacyAddr: String): String {
            val json: JSONObject? = JSONHelper().getJsonObject("https://rest.bitcoin.com/v2/cashAccounts/reverselookup/$cashAddr")
            if (json != null) {
                val results = json.getJSONArray("results")

                if (results.length() > 0) {
                    val name = results.getJSONObject(0).getString("nameText")
                    val accountNumber = results.getJSONObject(0).getInt("accountNumber")
                    val accountHash = results.getJSONObject(0).getString("accountHash")
                    val collisionLength = results.getJSONObject(0).getInt("accountCollisionLength")
                    val collisionIdentifier = accountHash.substring(0, collisionLength)

                    return if (collisionIdentifier.isNotEmpty()) {
                        val cashAcct = "$name#$accountNumber.$collisionIdentifier"
                        PrefsUtil.prefs.edit().putString("cashacct_$legacyAddr", cashAcct).apply()
                        cashAcct
                    } else {
                        val cashAcct = "$name#$accountNumber"
                        PrefsUtil.prefs.edit().putString("cashacct_$legacyAddr", cashAcct).apply()
                        cashAcct
                    }
                }
            }

            PrefsUtil.prefs.edit().putString("cashacct_$legacyAddr", "none_found").apply()
            return "No Cash Account"
        }

        private val cashAcctServers = arrayOf("https://cashacct.imaginary.cash", "https://electrum.imaginary.cash", "https://cashaccounts.bchdata.cash", "https://rest.bitcoin.com/v2/cashAccounts")
        private val blockExplorers = arrayOf("rest.bitcoin.com")
        private val blockExplorerAPIURL = arrayOf("https://rest.bitcoin.com/v2/transaction/details/")

        private val entropy: ByteArray
            get() = getEntropy(SecureRandom())

        val price: Double
            get() {
                return readPriceFromUrl("https://api.cryptowat.ch/markets/coinbase-pro/bchusd/price")
            }

        val priceEur: Double
            get() {
                return readPriceFromUrl("https://api.cryptowat.ch/markets/coinbase-pro/bcheur/price")
            }

        val priceAud: Double
            get() {
                return readPriceFromUrl("https://min-api.cryptocompare.com/data/price?fsym=BCH&tsyms=AUD")
            }

        private fun readPriceFromUrl(url: String): Double {
            var price = 0.0

            try {
                val json = JSONHelper().getJsonObject(url)
                val priceStr = when {
                    url.contains("min-api.cryptocompare.com") -> {
                        json!!.getDouble("AUD")
                    }
                    else -> {
                        json!!.getJSONObject("result").getDouble("price")
                    }
                }

                println(priceStr)
                price = priceStr
            } catch (e: Exception) {
                e.printStackTrace()
                price = 0.0
            }

            return price
        }


        private fun getEntropy(random: SecureRandom): ByteArray {
            val seed = ByteArray(DeterministicSeed.DEFAULT_SEED_ENTROPY_BITS / 8)
            random.nextBytes(seed)
            return seed
        }
    }
}