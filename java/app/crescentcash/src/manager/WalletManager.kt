package app.crescentcash.src.manager

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.CountDownTimer
import android.os.Handler
import android.os.Vibrator
import android.text.TextUtils
import android.view.View
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import app.crescentcash.src.MainActivity
import app.crescentcash.src.R
import app.crescentcash.src.activity.PaymentReceivedActivity
import app.crescentcash.src.activity.SendActivity
import app.crescentcash.src.activity.SendSLPActivity
import app.crescentcash.src.uri.URIHelper
import app.crescentcash.src.utils.Constants
import app.crescentcash.src.utils.OpPushParser
import app.crescentcash.src.utils.PrefsUtil
import app.crescentcash.src.utils.UtxoUtil
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.protobuf.ByteString
import com.vdurmont.emoji.EmojiParser
import org.bitcoinj.core.*
import org.bitcoinj.core.bip47.BIP47Channel
import org.bitcoinj.core.listeners.DownloadProgressTracker
import org.bitcoinj.crypto.BIP38PrivateKey
import org.bitcoinj.crypto.KeyCrypterScrypt
import org.bitcoinj.kits.BIP47AppKit
import org.bitcoinj.kits.SlpAppKit
import org.bitcoinj.net.NetHelper
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.params.TestNet3Params
import org.bitcoinj.protocols.payments.PaymentProtocol
import org.bitcoinj.protocols.payments.PaymentProtocolException
import org.bitcoinj.protocols.payments.PaymentSession
import org.bitcoinj.protocols.payments.slp.SlpPaymentSession
import org.bitcoinj.script.ScriptBuilder
import org.bitcoinj.script.ScriptOpCodes
import org.bitcoinj.utils.MonetaryFormat
import org.bitcoinj.utils.Threading
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.Protos
import org.bitcoinj.wallet.SendRequest
import org.bitcoinj.wallet.Wallet
import org.bouncycastle.util.encoders.Hex
import java.io.File
import java.math.BigDecimal
import java.net.InetAddress
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor

class WalletManager {
    companion object {
        val walletDir: File = File(MainActivity.getDataDir())
        lateinit var currentEcKey: ECKey
        var selectedUtxos: ArrayList<TransactionOutput> = ArrayList()
        var tokenList = ArrayList<Map<String, String>>()
        lateinit var currentTokenId: String
        var currentTokenPosition: Int = 0
        var walletKit: BIP47AppKit? = null
        var slpWalletKit: SlpAppKit? = null
        var parameters: NetworkParameters = if (Constants.IS_PRODUCTION) MainNetParams.get() else TestNet3Params.get()
        var addOpReturn: Boolean = false
        var encrypted: Boolean = false
        var useTor: Boolean = false
        var allowLegacyP2SH: Boolean = false
        lateinit var sendType: String
        lateinit var displayUnits: String
        lateinit var bchToSend: String
        var maximumAutomaticSend: Float = 0.0f
        var registeredTxHash: String? = null
        var registeredBlockHash: String? = null
        var registeredBlock: String? = null
        lateinit var timer: CountDownTimer
        var downloading: Boolean = false
        var downloadingSlp: Boolean = false
        lateinit var issuedKeysArrayList: ArrayList<Map<String, ECKey>>
        val utxoMap = ArrayList<Map<String, TransactionOutput>>()
        lateinit var txList: ArrayList<Transaction>

        val SCRYPT_PARAMETERS: Protos.ScryptParameters = Protos.ScryptParameters.newBuilder().setP(6).setR(8).setN(32768).setSalt(ByteString.copyFrom(KeyCrypterScrypt.randomSalt())).build()

        val wallet: Wallet
            get() = walletKit!!.getvWallet()

        fun setBitcoinSDKThread() {
            val handler = Handler()
            Threading.USER_THREAD = Executor { handler.post(it) }
        }

        fun getXpub(): String {
            return wallet.watchingKey.serializePubB58(parameters)
        }

        fun isAddressMine(address: String): Boolean {
            val addressObj = LegacyAddress.fromBase58(parameters, address)

            return wallet.isPubKeyHashMine(addressObj.hash)
        }

        fun getBalance(wallet: Wallet): Coin {
            return wallet.getBalance(Wallet.BalanceType.ESTIMATED)
        }

        fun getWalletKeys(): List<ECKey> {
            return this.wallet.issuedReceiveKeys
        }

        fun getPrivateKey(index: Int): ECKey {
            val keys = getWalletKeys()
            return keys[index]
        }

        fun sweepWallet(activity: Activity, privKey: String) {
            object : Thread() {
                override fun run() {
                    val utxoUtil = UtxoUtil()
                    val key = getPrivateKeyFromString(privKey)
                    utxoUtil.setupSweepWallet(key)
                    val txCount = utxoUtil.getUtxos()
                    if (txCount > 0) {
                        utxoUtil.sweep(wallet.currentReceiveAddress().toString())
                    }

                    val intent = Intent(Constants.ACTION_CLEAR_SWEEP_TEXT)
                    LocalBroadcastManager.getInstance(activity).sendBroadcast(intent)
                }
            }.start()
        }

        fun getPrivateKeyFromString(privKey: String): ECKey {
            val key = if (privKey.length == 51 || privKey.length == 52) {
                val dumpedPrivateKey = DumpedPrivateKey.fromBase58(MainNetParams.get(), privKey)
                dumpedPrivateKey.key
            } else {
                val privateKey = Base58.decodeToBigInteger(privKey)
                ECKey.fromPrivate(privateKey)
            }

            return key
        }

        fun signMessageWithKey(ecKey: ECKey, message: String): String {
            return ecKey.signMessage(message)
        }

        fun isSignatureValid(address: String, signature: String, message: String): Boolean {
            try {
                val finalAddress = if (address.contains("#")) {
                    NetHelper().getCashAccountAddress(this.parameters, address, true)
                } else {
                    address
                }
                println(finalAddress)
                val signedAddress = ECKey.signedMessageToKey(message, signature).toAddress(MainNetParams.get()).toString()
                val addressLegacy = if (Address.isValidCashAddr(MainNetParams.get(), finalAddress)) {
                    LegacyAddress.fromCashAddress(parameters, finalAddress).toBase58()
                } else {
                    finalAddress
                }
                println(addressLegacy)
                println(signedAddress == addressLegacy)

                return signedAddress == addressLegacy
            } catch (e: Exception) {
                return false
            }
        }

        fun setupNodeOnStart() {
            val nodeIP = PrefsUtil.prefs.getString("networkNode", "")
            println("GETTING NODE IP: $nodeIP")
            assert(nodeIP != null)
            if (!nodeIP.equals("")) {
                this.walletKit!!.setPeerNodes(null)

                var node1: InetAddress? = null

                try {
                    node1 = InetAddress.getByName(nodeIP)
                } catch (e: UnknownHostException) {
                    e.printStackTrace()
                }


                this.walletKit!!.setPeerNodes(PeerAddress(parameters, node1))
            }
        }

        fun setupSlpNodeOnStart() {
            val nodeIP = PrefsUtil.prefs.getString("networkNode", "")
            assert(nodeIP != null)
            if (nodeIP != "") {
                this.slpWalletKit!!.setPeerNodes(null)
                var node1: InetAddress? = null

                try {
                    node1 = InetAddress.getByName(nodeIP)

                } catch (e: UnknownHostException) {
                    e.printStackTrace()
                }

                this.slpWalletKit!!.setPeerNodes(PeerAddress(parameters, node1))
            }
        }

        fun importPrivateKey(privKey: String) {
            val key = getPrivateKeyFromString(privKey)
            this.wallet.importKey(key)
        }

        fun registerCashAccount(ecKey: ECKey, name: String): String? {
            return try {
                val req = SendRequest.createCashAccount(this.parameters, ecKey.toAddress(this.parameters).toString(), name)
                req.allowUnconfirmed()
                req.ensureMinRequiredFee = false
                req.feePerKb = Coin.valueOf(java.lang.Long.parseLong(1.toString() + "") * 1000L)
                val tx = this.walletKit!!.getvWallet().sendCoinsOffline(req)
                this.broadcastTxToPeers(tx)
                tx.hashAsString
            } catch (e: InsufficientMoneyException) {
                null
            }
        }

        fun verify(activity: Activity, address: String, signature: String, message: String) {
            val isVerified = isSignatureValid(address, signature, message)

            activity.runOnUiThread {
                if (isVerified) {
                    UIManager.showToastMessage(activity, "Signature is valid!")
                } else {
                    UIManager.showToastMessage(activity, "Signature is NOT valid!")
                }
            }
        }

        fun getMaxValueOfSelectedUtxos(): Coin {
            var utxoAmount = 0.0
            for (x in 0 until this.selectedUtxos.size) {
                val utxo = this.selectedUtxos[x]
                utxoAmount += java.lang.Double.parseDouble(utxo.value.toPlainString())
            }

            val str = UIManager.formatBalanceNoUnit(utxoAmount, "#.########")
            return Coin.parseCoin(str)
        }

        fun isProtocol(tx: Transaction, protocolId: String): Boolean {
            for (x in 0 until tx.outputs.size) {
                val output = tx.outputs[x]
                if (output.scriptPubKey.isOpReturn) {
                    if (output.scriptPubKey.chunks[1].data != null) {
                        val protocolCode = String(Hex.encode(output.scriptPubKey.chunks[1].data!!), StandardCharsets.UTF_8)
                        return protocolCode == protocolId
                    }
                }
            }

            return false
        }

        fun sentToSatoshiDice(tx: Transaction): Boolean {
            for (x in 0 until tx.outputs.size) {
                val output = tx.outputs[x]
                if (output.scriptPubKey.isSentToAddress) {
                    val addressP2PKH = output.getAddressFromP2PKHScript(this.parameters).toString()

                    if (Address.isValidLegacyAddress(this.parameters, addressP2PKH) && !this.isAddressMine(addressP2PKH)) {
                        val satoshiDiceAddrs = arrayListOf(
                                "1DiceoejxZdTrYwu3FMP2Ldew91jq9L2u", "1Dice115YcjDrPM9gXFW8iFV9S3j9MtERm",
                                "1Dice1FZk6Ls5LKhnGMCLq47tg1DFG763e", "1Dice1cF41TGRLoCTbtN33DSdPtTujzUzx",
                                "1Dice1wBBY22stCobuE1LJxHX5FNZ7U97N", "1Dice5ycHmxDHUFVkdKGgrwsDDK1mPES3U",
                                "1Dice7JNVnvzyaenNyNcACuNnRVjt7jBrC", "1Dice7v1M3me7dJGtTX6cqPggwGoRADVQJ",
                                "1Dice2wTatMqebSPsbG4gKgT3HfHznsHWi", "1Dice81SKu2S1nAzRJUbvpr5LiNTzn7MDV",
                                "1Dice9GgmweQWxqdiu683E7bHfpb7MUXGd"
                        )
                        return satoshiDiceAddrs.indexOf(addressP2PKH) != -1
                    }
                }
            }

            return false
        }

        fun isCashShuffle(tx: Transaction): Boolean {
            if (tx.outputs.size >= tx.inputs.size && tx.outputs.size > 1 && tx.inputs.size > 1) {
                val shuffledOutputs = ArrayList<String>()
                for (x in 0 until tx.inputs.size) {
                    if (tx.outputs[x].scriptPubKey.isSentToAddress)
                        shuffledOutputs.add(tx.outputs[x].value.toPlainString())
                    else
                        return false
                }

                val hashSet = HashSet(shuffledOutputs)
                return hashSet.size == 1
            }

            return false
        }

        fun broadcastTxToPeers(tx: Transaction) {
            for (peer in walletKit!!.peerGroup.connectedPeers) {
                peer.sendMessage(tx)
            }
        }

        fun setupWalletKit(activity: MainActivity, seed: DeterministicSeed?, cashAcctName: String, verifyingRestore: Boolean, upgradeToBip47: Boolean) {
            setBitcoinSDKThread()

            walletKit = BIP47AppKit().initialize(parameters, walletDir, Constants.WALLET_NAME, seed)
            walletKit?.setUseTor(useTor)
            walletKit?.setOnReceiveTxRunnable {
                if (!UIManager.isDisplayingDownload) {
                    activity.displayMyBalance(getBalance(wallet).toFriendlyString(), null)
                    activity.refresh()
                }
            }

            walletKit?.peerGroup?.setBloomFilterFalsePositiveRate(0.01)
            walletKit?.peerGroup?.isBloomFilteringEnabled = true
            setupWalletListeners(activity, walletKit!!.getvWallet())
            if (MainActivity.isNewUser) {
                val address = walletKit!!.getvWallet().currentReceiveAddress().toString()
                val paymentCode = walletKit!!.paymentCode
                println("Registering...")
                val editor = PrefsUtil.prefs.edit()
                editor.putBoolean("isNewUser", false)
                editor.putBoolean("usingNewBlockStore", true)
                editor.putBoolean("usingNewBlockStoreSlp", true)
                editor.apply()
                if (Constants.IS_PRODUCTION) NetManager.registerCashAccount(activity, cashAcctName, paymentCode, address)

                setupSlpWalletKit(activity, walletKit!!.getvWallet().keyChainSeed)
            } else {
                val keychainSeed = walletKit!!.getvWallet().keyChainSeed

                if (!keychainSeed.isEncrypted) {
                    /*
                    If the saved setting we got is true, but our wallet is unencrypted, then we set our saved setting to false.
                     */
                    if (encrypted) {
                        encrypted = false
                        PrefsUtil.prefs.edit().putBoolean("useEncryption", encrypted).apply()
                    }

                    setupSlpWalletKit(activity, walletKit!!.getvWallet().keyChainSeed)
                }

                if (!verifyingRestore) {
                    if (seed == null)
                        activity.refresh()
                } else {
                    if(upgradeToBip47) {
                        val address = walletKit!!.getvWallet().currentReceiveAddress().toString()
                        val paymentCode = walletKit!!.paymentCode
                        println("Upgrading...")
                        PrefsUtil.prefs.edit().putBoolean("isNewUser", false).apply()
                        if (Constants.IS_PRODUCTION) NetManager.registerCashAccount(activity, cashAcctName.split("#")[0], paymentCode, address)
                    }

                    val editor = PrefsUtil.prefs.edit()
                    editor.putBoolean("usingNewBlockStore", true).apply()
                    editor.putBoolean("usingNewBlockStoreSlp", true).apply()
                    editor.apply()
                }
            }

            setupNodeOnStart()

            walletKit!!.setDownloadProgressTracker(object : DownloadProgressTracker() {
                override fun progress(pct: Double, blocksSoFar: Int, date: Date) {
                    super.progress(pct, blocksSoFar, date)
                    val percentage = pct.toInt()
                    activity.runOnUiThread {
                        activity.displayPercentage(percentage)
                    }
                }

                override fun doneDownload() {
                    super.doneDownload()
                    activity.runOnUiThread {
                        activity.displayDownloadContent(false)
                        activity.refresh()
                    }
                }
            })

            val checkpointsInputStream = activity.assets.open("checkpoints.txt")
            walletKit!!.setCheckpoints(checkpointsInputStream)
            walletKit!!.startAsync()
        }

        private fun setupWalletListeners(activity: MainActivity, wallet: Wallet) {
            wallet.addCoinsReceivedEventListener { wallet1, tx, prevBalance, newBalance ->
                if (!UIManager.isDisplayingDownload) {
                    activity.displayMyBalance(getBalance(wallet).toFriendlyString(), null)
                    val satValue = tx.getValueSentToMe(wallet1).value
                    if (tx.purpose == Transaction.Purpose.UNKNOWN) {
                        if(satValue != 546L) {
                            UIManager.playAudio(activity, R.raw.coins_received)
                            UIManager.startActivity(activity, PaymentReceivedActivity::class.java)
                        }
                    }

                    if(satValue != 546L) {
                        val v = activity.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

                        v.vibrate(100)
                    }

                    activity.refresh()
                }
            }
            wallet.addCoinsSentEventListener { wallet12, tx, prevBalance, newBalance ->
                if (!UIManager.isDisplayingDownload) {
                    activity.displayMyBalance(getBalance(wallet).toFriendlyString(), null)
                    UIManager.showToastMessage(activity, "Sent coins!")
                    val intent = Intent(Constants.ACTION_CLEAR_SEND)
                    LocalBroadcastManager.getInstance(activity).sendBroadcast(intent)
                    activity.refresh()

                    UIManager.playAudio(activity, R.raw.send_coins)
                }
            }
        }

        fun setupSlpWalletKit(activity: MainActivity, seed: DeterministicSeed) {
            val mainHandler = Handler(activity.mainLooper)

            val runnable = Runnable {
                this.slpWalletKit = SlpAppKit().initialize(this.parameters, this.walletDir, "users_slp_wallet", seed)
                slpWalletKit?.setUseTor(useTor)
                this.slpWalletKit!!.setDownloadProgressTracker(object : DownloadProgressTracker() {
                    override fun progress(pct: Double, blocksSoFar: Int, date: Date) {
                        super.progress(pct, blocksSoFar, date)
                        val percentage = pct.toInt()
                        activity.runOnUiThread {
                            activity.displayPercentageSlp(percentage)
                        }
                    }

                    override fun doneDownload() {
                        super.doneDownload()
                        activity.runOnUiThread {
                            activity.displayDownloadContentSlp(false)
                            activity.refresh()
                        }
                    }
                })
                this.setupSlpNodeOnStart()
                val checkpointsInputStream = activity.assets.open("checkpoints.txt")
                this.slpWalletKit!!.setCheckpoints(checkpointsInputStream)
                this.setupSlpWalletListeners(activity, this.getSlpWallet())

                val intent = Intent(Constants.ACTION_UPDATE_HOME_SCREEN_BALANCE)
                LocalBroadcastManager.getInstance(activity).sendBroadcast(intent)
                this.slpWalletKit!!.startAsync()
            }

            mainHandler.post(runnable)
        }

        private fun setupSlpWalletListeners(activity: MainActivity, wallet: Wallet) {
            wallet.addCoinsReceivedEventListener { wallet1, tx, prevBalance, newBalance ->
                if (!downloadingSlp) {
                    activity.displayMyBalance(getBalance(wallet).toFriendlyString(), this.getSlpWallet().getBalance(Wallet.BalanceType.ESTIMATED).toFriendlyString())
                    val satValue = tx.getValueSentToMe(wallet1).value

                    if (tx.purpose == Transaction.Purpose.UNKNOWN) {
                        if(satValue != 546L) {
                            UIManager.playAudio(activity, R.raw.coins_received)
                            UIManager.startActivity(activity, PaymentReceivedActivity::class.java)
                        }
                    }

                    if(satValue != 546L) {
                        val v = activity.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

                        v.vibrate(100)
                    }

                    activity.refresh()
                }
            }
            wallet.addCoinsSentEventListener { wallet12, tx, prevBalance, newBalance ->
                if (!downloadingSlp) {
                    activity.displayMyBalance(getBalance(wallet).toFriendlyString(), this.getSlpWallet().getBalance(Wallet.BalanceType.ESTIMATED).toFriendlyString())
                    UIManager.showToastMessage(activity, "Sent coins!")
                    val intent = Intent(Constants.ACTION_CLEAR_SLP_SEND)
                    LocalBroadcastManager.getInstance(activity).sendBroadcast(intent)
                    activity.refresh()
                    UIManager.playAudio(activity, R.raw.send_coins)
                }
            }
        }

        fun getSlpKit(): SlpAppKit {
            return slpWalletKit!!
        }

        fun getSlpWallet(): Wallet {
            return slpWalletKit!!.wallet
        }

        fun getBIP70Data(sendActivity: SendActivity, url: String) {
            object : Thread() {
                override fun run() {
                    try {
                        val future: ListenableFuture<PaymentSession> = PaymentSession.createFromUrl(url)

                        val session = future.get()

                        val amountWanted = session.value

                        val amountFormatted = URIHelper().processSendAmount(amountWanted.toPlainString())
                        val amountAsFloat = amountFormatted.toFloat()
                        sendActivity.runOnUiThread { sendActivity.amountText.text = amountFormatted }

                        if (amountAsFloat <= this@Companion.maximumAutomaticSend) {
                            sendActivity.runOnUiThread { sendActivity.btnSendSlider.completeSlider() }
                        }
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    } catch (e: ExecutionException) {
                        e.printStackTrace()
                    } catch (e: PaymentProtocolException) {
                        e.printStackTrace()
                    }

                }
            }.start()
        }

        fun getBIP70Data(sendActivity: SendSLPActivity, url: String) {
            object : Thread() {
                override fun run() {
                    try {
                        val future: ListenableFuture<PaymentSession> = PaymentSession.createFromUrl(url)

                        val session = future.get()

                        val amountWanted = session.value

                        val amountFormatted = URIHelper().processSendAmount(amountWanted.toPlainString())
                        val amountAsFloat = amountFormatted.toFloat()
                        sendActivity.runOnUiThread { sendActivity.slpAmount.text = amountFormatted }

                        if (amountAsFloat <= this@Companion.maximumAutomaticSend) {
                            sendActivity.runOnUiThread { sendActivity.btnSendSLPSlider.completeSlider() }
                        }
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    } catch (e: ExecutionException) {
                        e.printStackTrace()
                    } catch (e: PaymentProtocolException) {
                        e.printStackTrace()
                    }

                }
            }.start()
        }

        fun getSlpBIP70Data(sendActivity: SendSLPActivity, url: String) {
            object : Thread() {
                override fun run() {
                    try {
                        val future: ListenableFuture<SlpPaymentSession> = SlpPaymentSession.createFromUrl(url)

                        val session = future.get()

                        val tokenId = session.tokenId
                        val tokensWanted = session.totalTokenAmount
                        val slpToken = slpWalletKit?.getSlpToken(tokenId)
                        val tokensWantedFormatted: Double = BigDecimal.valueOf(tokensWanted).scaleByPowerOfTen(-slpToken?.decimals!!).toDouble()
                        sendActivity.runOnUiThread { sendActivity.slpAmount.text = if(slpToken.decimals == 0) {
                            tokensWantedFormatted.toInt().toString()
                            } else {
                                tokensWantedFormatted.toString()
                            }
                        }
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    } catch (e: ExecutionException) {
                        e.printStackTrace()
                    } catch (e: PaymentProtocolException) {
                        e.printStackTrace()
                    }

                }
            }.start()
        }

        fun send(sendActivity: SendActivity) {
            if (!UIManager.isDisplayingDownload) {
                sendActivity.btnSendSlider.isEnabled = false
                val amount = sendActivity.amount
                val amtDblToFrmt: Double

                amtDblToFrmt = if (!TextUtils.isEmpty(amount)) {
                    java.lang.Double.parseDouble(amount)
                } else {
                    0.0
                }

                val formatter = DecimalFormat("#.########", DecimalFormatSymbols(Locale.US))
                val amtDblFrmt = formatter.format(amtDblToFrmt)
                var amtToSend = java.lang.Double.parseDouble(amtDblFrmt)

                if (sendType == "USD" || sendType == "EUR" || sendType == "AUD") {
                    object : Thread() {
                        override fun run() {
                            if (sendType == "USD") {
                                val usdToBch = amtToSend / NetManager.price
                                bchToSend = formatter.format(usdToBch)
                                processPayment(sendActivity)
                            }

                            if (sendType == "EUR") {
                                val usdToBch = amtToSend / NetManager.priceEur
                                bchToSend = formatter.format(usdToBch)
                                processPayment(sendActivity)
                            }

                            if (sendType == "AUD") {
                                val usdToBch = amtToSend / NetManager.priceAud
                                bchToSend = formatter.format(usdToBch)
                                processPayment(sendActivity)
                            }
                        }
                    }.start()
                } else {
                    if (sendType == MonetaryFormat.CODE_BTC) {
                        println("No formatting needed")
                        bchToSend = formatter.format(amtToSend)
                    }

                    if (sendType == MonetaryFormat.CODE_MBTC) {
                        val mBTCToSend = amtToSend
                        amtToSend = mBTCToSend / 1000.0
                        bchToSend = formatter.format(amtToSend)
                    }

                    if (sendType == MonetaryFormat.CODE_UBTC) {
                        val uBTCToSend = amtToSend
                        amtToSend = uBTCToSend / 1000000.0
                        bchToSend = formatter.format(amtToSend)
                    }

                    if (sendType == "sats") {
                        val satsToSend = amtToSend.toLong()
                        amtToSend = satsToSend / 100000000.0
                        bchToSend = formatter.format(amtToSend)
                    }

                    processPayment(sendActivity)
                }
            } else {
                throwSendError(sendActivity, "Wallet hasn't finished syncing!")
            }
        }

        private fun processPayment(sendActivity: SendActivity) {
            println(bchToSend)
            if (!TextUtils.isEmpty(sendActivity.amount) && !TextUtils.isEmpty(bchToSend) && sendActivity.amountText.text != null && bchToSend != "") {
                val amtCheckVal = java.lang.Double.parseDouble(Coin.parseCoin(bchToSend).toPlainString())

                if (TextUtils.isEmpty(sendActivity.recipient)) {
                    throwSendError(sendActivity, "Please enter a recipient.")
                } else if (amtCheckVal < 0.00001) {
                    throwSendError(sendActivity, "Enter a valid amount. Minimum is 0.00001 BCH")
                } else if (this.getBalance(wallet).isLessThan(Coin.parseCoin(amtCheckVal.toString()))) {
                    throwSendError(sendActivity, "Insufficient balance!")
                } else {
                    val recipientText = sendActivity.recipient
                    val uri = URIHelper(recipientText, false)
                    val address = uri.address

                    val amount = if (uri.amount != "null")
                        uri.amount
                    else
                        "null"

                    if (amount != "null")
                        bchToSend = amount

                    if (address.startsWith("http")) {
                        sendActivity.runOnUiThread {
                            sendActivity.displayRecipientAddress(address)
                            sendActivity.sendTypeSpinner.setSelection(0)
                        }
                        this.sendType = this.displayUnits
                        PrefsUtil.prefs.edit().putString("sendType", this.sendType).apply()

                        this.processBIP70(sendActivity, address)
                    } else if (address.startsWith("+")) {
                        val rawNumber = URIHelper().getRawPhoneNumber(address)
                        val numberString = rawNumber.replace("+", "")
                        var amtToSats = java.lang.Double.parseDouble(bchToSend)
                        val satFormatter = DecimalFormat("#", DecimalFormatSymbols(Locale.US))
                        amtToSats *= 100000000
                        val sats = satFormatter.format(amtToSats).toInt()
                        val url = "https://pay.cointext.io/p/$numberString/$sats"
                        println(url)
                        this.processBIP70(sendActivity, url)
                    } else {
                        if (address.contains("#")) {
                            val toAddressFixed = EmojiParser.removeAllEmojis(address)
                            val toAddressStripped = toAddressFixed.replace("; ", "")
                            sendCoins(sendActivity, bchToSend, toAddressStripped)
                        } else {
                            try {
                                sendCoins(sendActivity, bchToSend, address)
                            } catch (e: AddressFormatException) {
                                e.printStackTrace()
                                throwSendError(sendActivity, "Invalid address!")
                            }
                        }
                    }
                }
            } else {
                throwSendError(sendActivity, "Please enter an amount.")
            }
        }

        private fun sendCoins(sendActivity: SendActivity, amount: String, toAddress: String) {
            object : Thread() {
                override fun run() {
                    try {
                        if (toAddress.contains("#")) {
                            var address: String? = null
                            try {
                                address = NetHelper().getCashAccountAddress(parameters, toAddress)
                            } catch (e: Exception) {
                                throwSendError(sendActivity, "Error getting Cash Account")
                            }

                            if(address != null) {
                                if (Address.isValidPaymentCode(address)) {
                                    println("Valid BIP47 Payment Code from Cash Account")
                                    val paymentChannel: BIP47Channel? = walletKit?.getBip47MetaForPaymentCode(address)

                                    if (paymentChannel == null) {
                                        //If payment channel is null, we can assume notification tx has not been sent. It's possible one has been sent in the past, but the payment channel is null because the user deleted the .bip47 file, but that's a non-issue.
                                        println("Constructing notification tx...")
                                        val notification = walletKit?.makeNotificationTransaction(address, true)
                                        walletKit?.broadcastTransaction(notification?.tx)
                                        walletKit?.putPaymenCodeStatusSent(address, notification?.tx)
                                        this@Companion.attemptBip47Payment(sendActivity, amount, address)
                                    } else {
                                        this@Companion.attemptBip47Payment(sendActivity, amount, address)
                                    }
                                } else if (Address.isValidCashAddr(parameters, address) || Address.isValidLegacyAddress(parameters, address) && (!LegacyAddress.fromBase58(parameters, address).p2sh || allowLegacyP2SH)) {
                                    this@Companion.finalizeTransaction(sendActivity, amount, address)
                                }
                            } else {
                                throwSendError(sendActivity, "Invalid address!")
                            }
                        } else {
                            if (Address.isValidPaymentCode(toAddress)) {
                                println("Valid BIP47 Payment Code from Address")
                                val paymentChannel: BIP47Channel? = walletKit?.getBip47MetaForPaymentCode(toAddress)

                                if(paymentChannel == null) {
                                    //If payment channel is null, we can assume notification tx has not been sent. It's possible one has been sent in the past, but the payment channel is null because the user deleted the .bip47 file, but that's a non-issue.
                                    println("Constructing notification tx...")
                                    val notification = walletKit?.makeNotificationTransaction(toAddress, true)
                                    walletKit?.broadcastTransaction(notification?.tx)
                                    walletKit?.putPaymenCodeStatusSent(toAddress, notification?.tx)
                                    this@Companion.attemptBip47Payment(sendActivity, amount, toAddress)
                                } else {
                                    this@Companion.attemptBip47Payment(sendActivity, amount, toAddress)
                                }
                            } else if(Address.isValidCashAddr(parameters, toAddress) || Address.isValidLegacyAddress(parameters, toAddress) && (!LegacyAddress.fromBase58(parameters, toAddress).p2sh || allowLegacyP2SH)) {
                                this@Companion.finalizeTransaction(sendActivity, amount, toAddress)
                            }
                        }
                    } catch (e: java.lang.Exception) {
                        e.printStackTrace()
                        e.message?.let { throwSendError(sendActivity, it) }
                    }
                }
            }.start()
        }

        private fun attemptBip47Payment(sendActivity: SendActivity, amount: String, paymentCode: String) {
            val paymentChannel: BIP47Channel? = walletKit?.getBip47MetaForPaymentCode(paymentCode)
            var depositAddress: String? = null
            if (paymentChannel != null) {
                if(paymentChannel.isNotificationTransactionSent) {
                    depositAddress = walletKit?.getCurrentOutgoingAddress(paymentChannel).toString()
                    println("Received user's deposit address $depositAddress")
                    paymentChannel.incrementOutgoingIndex()
                    walletKit?.saveBip47MetaData()
                    this.finalizeTransaction(sendActivity, amount, depositAddress)
                } else {
                    val notification = walletKit?.makeNotificationTransaction(paymentCode, true)
                    walletKit?.broadcastTransaction(notification?.tx)
                    walletKit?.putPaymenCodeStatusSent(paymentCode, notification?.tx)
                    this.attemptBip47Payment(sendActivity, amount, paymentCode)
                }
            }
        }

        private fun finalizeTransaction(sendActivity: SendActivity, amount: String, toAddress: String) {
            val coinAmt = Coin.parseCoin(amount)

            if (coinAmt.getValue() > 0.0) {
                try {
                    val req: SendRequest
                    val cachedAddOpReturn = addOpReturn

                    if (useTor) {
                        if (selectedUtxos.size == 0) {
                            if (coinAmt >= getBalance(walletKit!!.getvWallet())) {
                                req = SendRequest.emptyWallet(parameters, toAddress, NetManager.torProxy)
                                /*
                                    Bitcoincashj requires emptying the wallet to only have a single output for some reason.
                                    So, we cache the original setting above, then set the real setting to false here.

                                    After doing the if(addOpReturn) check below, we restore it to its actual setting.
                                */
                                addOpReturn = false
                            } else {
                                req = SendRequest.to(parameters, toAddress, coinAmt, NetManager.torProxy)
                            }
                        } else {
                            if (coinAmt == getMaxValueOfSelectedUtxos()) {
                                req = SendRequest.emptyWallet(parameters, toAddress, NetManager.torProxy)
                                addOpReturn = false
                            } else {
                                req = SendRequest.to(parameters, toAddress, coinAmt, NetManager.torProxy)
                            }
                        }
                    } else {
                        if (selectedUtxos.size == 0) {
                            if (coinAmt >= getBalance(walletKit!!.getvWallet())) {
                                req = SendRequest.emptyWallet(parameters, toAddress)
                                /*
                                    Bitcoincashj requires emptying the wallet to only have a single output for some reason.
                                    So, we cache the original setting above, then set the real setting to false here.

                                    After doing the if(addOpReturn) check below, we restore it to its actual setting.
                                */
                                addOpReturn = false
                            } else {
                                req = SendRequest.to(parameters, toAddress, coinAmt)
                            }
                        } else {
                            if (coinAmt == getMaxValueOfSelectedUtxos()) {
                                req = SendRequest.emptyWallet(parameters, toAddress)
                                addOpReturn = false
                            } else {
                                req = SendRequest.to(parameters, toAddress, coinAmt)
                            }
                        }
                    }

                    req.allowUnconfirmed()
                    req.ensureMinRequiredFee = false
                    req.utxos = selectedUtxos
                    req.feePerKb = Coin.valueOf(2L * 1000L)

                    if (addOpReturn) {
                        if (sendActivity.opReturnBox.visibility != View.GONE) {
                            val opReturnText = sendActivity.opReturnText.text.toString()
                            if (opReturnText.isNotEmpty()) {
                                var scriptBuilder = ScriptBuilder().op(ScriptOpCodes.OP_RETURN)
                                val opPushParser = OpPushParser(opReturnText)
                                for (x in 0 until opPushParser.pushData.size) {
                                    val opPush = opPushParser.pushData[x]

                                    if (opPush.binaryData.size <= Constants.MAX_OP_RETURN) {
                                        scriptBuilder = scriptBuilder.data(opPush.binaryData)
                                    }
                                }

                                req.tx.addOutput(Coin.ZERO, scriptBuilder.build())
                            }
                        }
                    }

                    addOpReturn = cachedAddOpReturn

                    val tx = walletKit!!.getvWallet().sendCoinsOffline(req)
                    val txHexBytes = Hex.encode(tx.bitcoinSerialize())
                    val txHex = String(txHexBytes, StandardCharsets.UTF_8)
                    broadcastTxToPeers(tx)

                    if (!useTor) {
                        NetManager.broadcastTransaction(sendActivity, txHex, "https://rest.bitcoin.com/v2/rawtransactions/sendRawTransaction")
                    }

                    NetManager.broadcastTransaction(sendActivity, txHex, "https://rest.imaginary.cash/v2/rawtransactions/sendRawTransaction")
                } catch (e: InsufficientMoneyException) {
                    e.printStackTrace()
                    e.message?.let { throwSendError(sendActivity, it) }
                } catch (e: Wallet.CouldNotAdjustDownwards) {
                    e.printStackTrace()
                    throwSendError(sendActivity, "Not enough BCH for fee!")
                } catch (e: Wallet.ExceededMaxTransactionSize) {
                    e.printStackTrace()
                    throwSendError(sendActivity, "Transaction is too large!")
                } catch (e: NullPointerException) {
                    e.printStackTrace()
                    throwSendError(sendActivity, "Cash Account not found.")
                }

            }
        }

        private fun processBIP70(sendActivity: SendActivity, url: String) {
            try {
                val future: ListenableFuture<PaymentSession> = PaymentSession.createFromUrl(url)

                val session = future.get()
                if (session.isExpired) {
                    throwSendError(sendActivity, "Invoice expired!")
                }

                val req = session.sendRequest
                req.allowUnconfirmed()
                wallet.completeTx(req)

                val ack = session.sendPayment(ImmutableList.of(req.tx), wallet.freshReceiveAddress(), null)
                if (ack != null) {
                    Futures.addCallback<PaymentProtocol.Ack>(ack, object : FutureCallback<PaymentProtocol.Ack> {
                        override fun onSuccess(ack: PaymentProtocol.Ack?) {
                            wallet.commitTx(req.tx)
                            sendActivity.runOnUiThread {
                                sendActivity.clearSend()
                            }
                        }

                        override fun onFailure(throwable: Throwable) {
                            throwSendError(sendActivity, "An error occurred.")
                        }
                    }, MoreExecutors.directExecutor())
                }
            } catch (e: InsufficientMoneyException) {
                throwSendError(sendActivity, "You do not have enough BCH!")
            }
        }

        fun throwSendError(sendActivity: SendActivity, message: String) {
            sendActivity.runOnUiThread {
                UIManager.showToastMessage(sendActivity, message)
                sendActivity.setSendButtonsActive()
            }
        }

        fun blockieAddressFromTokenId(tokenId: String): String {
            return tokenId.slice(IntRange(12, tokenId.count() - 1))
        }

        fun isEncryptedBIP38Key(privKey: String): Boolean {
            return try {
                BIP38PrivateKey.fromBase58(this.parameters, privKey)
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}