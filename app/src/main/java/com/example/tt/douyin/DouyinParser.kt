package com.example.tt.douyin

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Ket qua doc duoc tu trang chia se / API Douyin (chua phu thuoc Android -> unit test duoc).
 *
 * @param playUrl link MP4 KHONG logo (null neu la album anh / video rieng tu)
 * @param durationMs thoi luong theo mili-giay (Douyin tra ve ms)
 */
data class DouyinMedia(
    val awemeId: String,
    val title: String,
    val author: String,
    val coverUrl: String,
    val playUrl: String?,
    val durationMs: Long,
    val fileSize: Long,
    val imageCount: Int
) {
    /** Co the dung de tai video (hoac biet chac day la album anh). */
    val isUsable: Boolean get() = playUrl != null || imageCount > 0
}

/**
 * Parser HTML/JSON Douyin — thuan Gson, khong phu thuoc Android.
 *
 * Luong chinh: trang `https://www.iesdouyin.com/share/video/{id}` (UA mobile)
 * nha ra `window._ROUTER_DATA` chua JSON. Cac API du phong tra ve cung cau truc item.
 */
object DouyinParser {

    private const val ROUTER_MARK = "window._ROUTER_DATA"

    private val PATH_ID = Regex(
        """/(?:video|note|shipin|slides)/(\d{6,})""",
        RegexOption.IGNORE_CASE
    )
    private val QUERY_ID = Regex(
        """[?&](?:aweme_id|modal_id|item_ids|item_id)=(\d{6,})""",
        RegexOption.IGNORE_CASE
    )

    /** Lay aweme_id tu link day du (link rut gon `v.douyin.com/xxx` phai theo redirect truoc). */
    fun extractAwemeId(url: String): String? =
        PATH_ID.find(url)?.groupValues?.getOrNull(1)
            ?: QUERY_ID.find(url)?.groupValues?.getOrNull(1)

    /**
     * Tim JSON cua `window._ROUTER_DATA` trong HTML.
     * Phai can bang ngoac vi JSON long nhieu cap va co the chua `}` trong chuoi.
     */
    fun extractRouterData(html: String): String? {
        val mark = html.indexOf(ROUTER_MARK)
        if (mark < 0) return null
        val equals = html.indexOf('=', mark)
        val start = html.indexOf('{', if (equals >= 0) equals else mark)
        if (start < 0) return null
        return balancedJson(html, start)
    }

    /** Doc HTML trang share -> media (null neu khong tim thay du lieu). */
    fun parseShareHtml(html: String, fallbackId: String = ""): DouyinMedia? {
        val json = extractRouterData(html) ?: return null
        return parseRouterJson(json, fallbackId)
    }

    /** API cu: `iesdouyin.com/web/api/v2/aweme/iteminfo/?item_ids=...` */
    fun parseItemListJson(json: String, fallbackId: String = ""): DouyinMedia? {
        val root = rootObject(json) ?: return null
        firstItem(root)?.let { return parseAweme(it, fallbackId) }
        return parseRouterJson(json, fallbackId)
    }

    /** API web: `douyin.com/aweme/v1/web/aweme/detail/?aweme_id=...` */
    fun parseAwemeDetailJson(json: String, fallbackId: String = ""): DouyinMedia? {
        val root = rootObject(json) ?: return null
        val detail = root.obj("aweme_detail") ?: root.obj("awemeDetail") ?: return null
        return parseAweme(detail, fallbackId)
    }

    // ---------------- ROUTER DATA -> media ----------------

    private fun parseRouterJson(json: String, fallbackId: String): DouyinMedia? {
        val root = rootObject(json) ?: return null
        val loader = root.obj("loaderData")
        if (loader != null) {
            for ((_, value) in loader.entrySet()) {
                val page = value as? JsonObject ?: continue
                val videoInfo = page.obj("videoInfoRes") ?: page.obj("videoInfo") ?: continue
                firstItem(videoInfo)?.let { return parseAweme(it, fallbackId) }
            }
        }
        // Mot so API tra ve thang { item_list: [...] }
        firstItem(root)?.let { return parseAweme(it, fallbackId) }
        return null
    }

    private fun parseAweme(aweme: JsonObject, fallbackId: String): DouyinMedia {
        val video = aweme.obj("video")
        val playAddr = video?.obj("play_addr") ?: video?.obj("playAddr")
        val rawPlay = playAddr?.arr("url_list").firstString()
            ?: playAddr?.arr("urlList").firstString()

        val images = aweme.arr("images")
            ?: aweme.obj("image_post_info")?.arr("images")
            ?: aweme.obj("imagePostInfo")?.arr("images")

        val durationMs = aweme.long("duration").takeIf { it > 0 }
            ?: video?.long("duration")?.takeIf { it > 0 }
            ?: 0L

        return DouyinMedia(
            awemeId = aweme.str("aweme_id") ?: aweme.str("awemeId") ?: fallbackId,
            title = aweme.str("desc") ?: aweme.str("title") ?: "",
            author = aweme.obj("author")?.str("nickname") ?: "",
            coverUrl = video?.obj("cover")?.arr("url_list").firstString()
                ?: video?.obj("origin_cover")?.arr("url_list").firstString()
                ?: video?.obj("dynamic_cover")?.arr("url_list").firstString()
                ?: "",
            playUrl = rawPlay?.let { stripWatermark(it) },
            durationMs = durationMs,
            fileSize = playAddr?.long("data_size") ?: 0L,
            imageCount = images?.size() ?: 0
        )
    }

    /** Bo dau logo: playwm -> play, va ep https de Android khong chan cleartext. */
    fun stripWatermark(url: String): String {
        val trimmed = url.trim()
        val https = if (trimmed.startsWith("http://", ignoreCase = true)) {
            "https://" + trimmed.substring("http://".length)
        } else {
            trimmed
        }
        return https.replace("playwm", "play")
    }

    // ---------------- JSON helpers ----------------

    private fun rootObject(json: String): JsonObject? =
        runCatching { JsonParser.parseString(json) as? JsonObject }.getOrNull()

    private fun firstItem(obj: JsonObject): JsonObject? {
        val list = obj.arr("item_list") ?: obj.arr("itemList") ?: return null
        return list.firstObject()
    }

    private fun JsonArray?.firstString(): String? {
        if (this == null) return null
        for (element in this) {
            if (element.isJsonNull) continue
            val value = runCatching { element.asString }.getOrNull()
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    private fun JsonArray.firstObject(): JsonObject? {
        for (element in this) {
            if (element is JsonObject) return element
        }
        return null
    }

    private fun JsonObject.obj(name: String): JsonObject? =
        get(name)?.takeIf(JsonElement::isJsonObject)?.asJsonObject

    private fun JsonObject.arr(name: String): JsonArray? =
        get(name)?.takeIf(JsonElement::isJsonArray)?.asJsonArray

    private fun JsonObject.str(name: String): String? {
        val element = get(name) ?: return null
        if (element.isJsonNull) return null
        return runCatching { element.asString }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun JsonObject.long(name: String): Long {
        val element = get(name) ?: return 0L
        if (element.isJsonNull) return 0L
        return runCatching { element.asLong }.getOrDefault(0L)
    }

    // ---------------- Can bang ngoac ----------------

    /**
     * Cat doan JSON bat dau tu `start` (phai la '{') den ngoac dong tuong ung.
     * Bo qua ngoac nam trong chuoi, xu ly `\"` va `\\`.
     */
    internal fun balancedJson(text: String, start: Int): String? {
        if (start !in text.indices || text[start] != '{') return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return text.substring(start, i + 1)
                    }
                }
            }
        }
        return null
    }
}
