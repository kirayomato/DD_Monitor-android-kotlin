import android.util.Log
import java.math.BigInteger
import java.net.URLEncoder
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object WbiSigner {

    // 混淆密钥编码表
    private val mixinKeyEncTab = listOf(
            46,
            47,
            18,
            2,
            53,
            8,
            23,
            32,
            15,
            50,
            10,
            31,
            58,
            3,
            45,
            35,
            27,
            43,
            5,
            49,
            33,
            9,
            42,
            19,
            29,
            28,
            14,
            39,
            12,
            38,
            41,
            13,
            37,
            48,
            7,
            16,
            24,
            55,
            40,
            61,
            26,
            17,
            0,
            1,
            60,
            51,
            30,
            4,
            22,
            25,
            54,
            21,
            56,
            59,
            6,
            63,
            57,
            62,
            11,
            36,
            20,
            34,
            44,
            52
                                       )

    // 请求头
    private val headers = mapOf(
            "Accept-Encoding" to "utf-8, deflate, zstd",
            "Accept-Language" to "zh-CN,zh;q=0.8,zh-TW;q=0.7,zh-HK;q=0.5,en;q=0.3,en-US;q=0.2",
            "Accept" to "application/json, text/plain, */*",
            "Cache-Control" to "no-cache",
            "Connection" to "keep-alive",
            "Origin" to "https://live.bilibili.com",
            "Referer" to "https://live.bilibili.com",
            "Pragma" to "no-cache",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36"
                               )

    // 获取当前时间戳
    private fun getCurrentTimestamp(): Long {
        return System.currentTimeMillis() / 1000
    }

    // 获取 img_key 和 sub_key
    suspend fun getWbiKeys(): Pair<String, String> = withContext(Dispatchers.IO) {
        var imgKey = ""
        var subKey = ""
        val client = OkHttpClient()
        val request = Request.Builder().url("https://api.bilibili.com/x/web-interface/nav")
            .headers(headers.toOkHttpHeaders()).build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            Log.e("BilibiliWbiSigner", "Failed to fetch wbi keys: HTTP ${response.code}")
        }
        response.body?.let {
            try {
                val jo = JSONObject(it.string())
                val data = jo.getJSONObject("data")
                val wbiImg = data.getJSONObject("wbi_img")

                imgKey = wbiImg.getString("img_url").split("/").last().split(".").first()
                subKey = wbiImg.getString("sub_url").split("/").last().split(".").first()
            }
            catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return@withContext Pair(imgKey, subKey)
    }

    // 打乱 key 顺序
    fun getMixinKey(orig: String): String {
        val sb = StringBuilder()
        for (i in mixinKeyEncTab) {
            if (sb.length >= 32) break
            sb.append(orig[i])
        }
        return sb.toString()
    }

    // 生成 wbi 签名
    fun encWbi(params: Map<String, Any>, imgKey: String, subKey: String): Map<String, Any> {
        if (imgKey.isBlank() || subKey.isBlank()) {
            return params
        }
        val mixinKey = getMixinKey(imgKey + subKey)
        val mutableParams = params.toMutableMap()
        mutableParams["wts"] = getCurrentTimestamp()

        // 排序参数
        val sortedEntries = mutableParams.entries.sortedBy { it.key }
        val filteredParams = mutableMapOf<String, Any>()
        for ((key, value) in sortedEntries) {
            val filteredValue = value.toString().filterNot { it in "!')*".toSet() }
            filteredParams[key] = filteredValue
        }

        // 构造查询字符串
        val queryBuilder = StringBuilder()
        for ((key, value) in filteredParams.entries.sortedBy { it.key }) {
            if (queryBuilder.isNotEmpty()) queryBuilder.append("&")
            queryBuilder.append(URLEncoder.encode(key, "UTF-8")).append("=")
                .append(URLEncoder.encode(value.toString(), "UTF-8"))
        }

        // 计算 md5
        val wbiSign = md5(queryBuilder.toString() + mixinKey)
        filteredParams["w_rid"] = wbiSign

        return filteredParams
    }

    // MD5 工具函数
    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return BigInteger(1, md.digest(input.toByteArray())).toString(16).padStart(32, '0')
    }

    // 将 Map<String, String> 转换为 OkHttp Headers
    private fun Map<String, String>.toOkHttpHeaders(): okhttp3.Headers {
        val headersBuilder = okhttp3.Headers.Builder()
        this.forEach { (key, value) -> headersBuilder.add(key, value) }
        return headersBuilder.build()
    }
}
