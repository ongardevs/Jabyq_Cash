package app.crescentcash.src.utils

import app.crescentcash.src.json.JSONHelper
import app.crescentcash.src.manager.NetManager
import app.crescentcash.src.manager.WalletManager
import org.bitcoinj.core.Coin
import org.bitcoinj.core.Context
import org.bitcoinj.core.ECKey
import org.bitcoinj.core.Transaction
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.wallet.SendRequest
import org.bitcoinj.wallet.Wallet
import org.bitcoinj.wallet.WalletTransaction
import org.json.JSONObject
import org.bouncycastle.util.encoders.Hex
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URL
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets


class UtxoUtil {
    lateinit var walletToSweep: Wallet
    lateinit var ecKey: ECKey
    fun setupSweepWallet(key: ECKey) {
        walletToSweep = Wallet.createBasic(WalletManager.parameters)
        walletToSweep.allowSpendingUnconfirmedTransactions()
        walletToSweep.importKey(key)
        ecKey = key

        println("Successfully setup sweep wallet")
    }

    fun getUtxos(): Int {
        val utxosJson = getUtxos("https://rest.bitcoin.com/v2/address/utxo/${ecKey.toAddress(MainNetParams.get())}")
        val txns = ArrayList<Transaction>()

        if (utxosJson != null) {
            val utxosArray = utxosJson.getJSONArray("utxos")
            for (x in 0 until utxosArray.length()) {
                val txid = utxosArray.getJSONObject(x).getString("txid")
                val tx = getTransaction(txid)

                if (tx != null && txns.indexOf(tx) == -1) {
                    txns.add(tx)
                }
            }
        }

        walletToSweep.clearTransactions(0)

        for (x in 0 until txns.size) {
            walletToSweep.addWalletTransaction(WalletTransaction(WalletTransaction.Pool.UNSPENT, txns[x]))
            println("Adding transaction to wallet... ${txns[x].hashAsString}")
        }

        println("Successfully grabbed UTXOs")
        return txns.size
    }

    fun sweep(toAddress: String) {
        println("Sweeping wallet...")
        val req: SendRequest = SendRequest.emptyWallet(MainNetParams.get(), toAddress)
        req.allowUnconfirmed()
        req.ensureMinRequiredFee = false
        req.feePerKb = Coin.valueOf(java.lang.Long.parseLong(1.toString() + "") * 1000L)
        val tx = walletToSweep.sendCoinsOffline(req)
        val txHexBytes = Hex.encode(tx.bitcoinSerialize())
        val txHex = String(txHexBytes, StandardCharsets.UTF_8)
        WalletManager.broadcastTxToPeers(tx)

        if (!WalletManager.useTor) {
            NetManager.broadcastTransaction(null, txHex, "https://rest.bitcoin.com/v2/rawtransactions/sendRawTransaction")
        }

        NetManager.broadcastTransaction(null, txHex, "https://rest.imaginary.cash/v2/rawtransactions/sendRawTransaction")
    }

    private fun getUtxos(url: String): JSONObject? {
        println("Grabbing UTXOs... $url")
        return try {
            JSONHelper().getJsonObject(url)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getTransaction(txId: String): Transaction? {
        val url = "https://rest.bitcoin.com/v2/rawtransactions/getRawTransaction/$txId?verbose=false"
        var `is`: InputStream? = null
        try {
            `is` = if (WalletManager.useTor) URL(url).openConnection(NetManager.torProxy).getInputStream() else URL(url).openConnection().getInputStream()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return try {
            val rd = BufferedReader(InputStreamReader(`is`, Charset.forName("UTF-8")))
            val jsonText = JSONHelper.readJSONFile(rd)
            val txHex = jsonText.replace("\"", "")
            Transaction(MainNetParams.get(), Hex.decode(txHex))
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try {
                `is`?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}