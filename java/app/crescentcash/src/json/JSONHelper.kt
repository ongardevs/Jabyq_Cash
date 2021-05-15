package app.crescentcash.src.json

import app.crescentcash.src.manager.NetManager
import app.crescentcash.src.manager.WalletManager
import org.json.JSONException
import org.json.JSONObject
import java.io.*
import java.net.URL
import java.nio.charset.Charset

class JSONHelper {

    fun getRegisterTxHash(jsonResponse: String): String? {
        var hash: String? = null
        hash = try {
            val json = JSONObject(jsonResponse)
            json.getString("txid")
        } catch (e: JSONException) {
            null
        }

        return hash
    }

    fun getJsonObject(url: String): JSONObject? {
        var `is`: InputStream? = null
        try {
            `is` = if (WalletManager.useTor) URL(url).openConnection(NetManager.torProxy).getInputStream() else URL(url).openConnection().getInputStream()
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }

        return try {
            val rd = BufferedReader(InputStreamReader(`is`, Charset.forName("UTF-8")))
            val jsonText = readJSONFile(rd)
            JSONObject(jsonText)
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

    companion object {

        @Throws(IOException::class)
        fun readJSONFile(rd: Reader): String {
            val sb = StringBuilder()
            while (true) {
                val cp = rd.read()

                if (cp != -1)
                    sb.append(cp.toChar())
                else
                    break
            }
            return sb.toString()
        }
    }
}
