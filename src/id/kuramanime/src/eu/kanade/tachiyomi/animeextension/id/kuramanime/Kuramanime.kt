package eu.kanade.tachiyomi.animeextension.id.kuramanime

import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import aniyomi.lib.doodextractor.DoodExtractor
import aniyomi.lib.filemoonextractor.FilemoonExtractor
import aniyomi.lib.streamtapeextractor.StreamTapeExtractor
import aniyomi.lib.streamwishextractor.StreamWishExtractor
import aniyomi.lib.vidguardextractor.VidGuardExtractor
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import keiyoushi.utils.ParsedAnimeHttpLegacySource
import keiyoushi.utils.bodyString
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parallelCatchingFlatMapBlocking
import keiyoushi.utils.useAsJsoup
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy

class Kuramanime :
    ParsedAnimeHttpLegacySource(),
    ConfigurableAnimeSource {
    override val name = "Kuramanime"

    override val baseUrl by lazy {
        preferences.getString(PREF_BASE_URL_KEY, DEFAULT_BASE_URL)!!.trimEnd('/')
    }

    override val lang = "id"

    override val supportsLatest = true

    private val preferences by getPreferencesLazy()
    private val json: Json by injectLazy()

    override val client: OkHttpClient by lazy {
        network.cloudflareClient.newBuilder()
            .addInterceptor(ScraperFallbackInterceptor(preferences, json))
            .build()
    }

    // ============================== Popular ===============================
    override fun popularAnimeRequest(page: Int) = GET("$baseUrl/anime?order_by=popular&page=$page")

    override fun popularAnimeSelector() = "div.product__item, div.filter__gallery > a"

    override fun popularAnimeFromElement(element: Element) = SAnime.create().apply {
        val link = element.selectFirst("a") ?: element
        setUrlWithoutDomain(link.attr("href"))
        thumbnail_url = element.selectFirst("div.set-bg")?.attr("data-setbg")
            ?: element.selectFirst("img")?.attr("src")
        title = (element.selectFirst("h5 > a, h5, div > h5")?.text() ?: link.text()).trim()
    }

    override fun popularAnimeNextPageSelector() = "div.product__pagination > a:last-child:not([aria-disabled='true'])"

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/anime?order_by=latest&page=$page")

    override fun latestUpdatesSelector() = popularAnimeSelector()

    override fun latestUpdatesFromElement(element: Element) = popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularAnimeNextPageSelector()

    // =============================== Search ===============================
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        if (query.isNotEmpty()) {
            val url = "$baseUrl/anime".toHttpUrl().newBuilder()
            url.addQueryParameter("search", query)
            url.addQueryParameter("page", page.toString())
            return GET(url.build().toString(), headers)
        } else {
            var url = "$baseUrl/anime".toHttpUrl().newBuilder()

            var orderBy = ""
            var statusPath = ""
            var typePath = ""
            var typeName = ""
            var genrePath = ""

            for (filter in filters) {
                when (filter) {
                    is KuramanimeFilters.OrderByFilter -> orderBy = filter.toUriPart()
                    is KuramanimeFilters.StatusFilter -> statusPath = filter.toUriPart()
                    is KuramanimeFilters.TypeFilter -> {
                        typePath = filter.toUriPart()
                        typeName = filter.toNamePart()
                    }
                    is KuramanimeFilters.GenreFilter -> genrePath = filter.toUriPart()
                    else -> {}
                }
            }

            when {
                statusPath.isNotEmpty() -> url = "$baseUrl/quick/$statusPath".toHttpUrl().newBuilder()
                typePath.isNotEmpty() -> {
                    url = "$baseUrl/properties/type/$typePath".toHttpUrl().newBuilder()
                    url.addQueryParameter("name", typeName)
                }
                genrePath.isNotEmpty() -> url = "$baseUrl/properties/genre/$genrePath".toHttpUrl().newBuilder()
            }

            if (orderBy.isNotEmpty()) {
                url.addQueryParameter("order_by", orderBy)
            }
            url.addQueryParameter("page", page.toString())

            return GET(url.build().toString(), headers)
        }
    }

    override fun searchAnimeSelector() = popularAnimeSelector()

    override fun searchAnimeFromElement(element: Element) = popularAnimeFromElement(element)

    override fun searchAnimeNextPageSelector() = popularAnimeNextPageSelector()

    // =========================== Anime Details ============================
    override fun animeDetailsParse(document: Document) = SAnime.create().apply {
        thumbnail_url = document.selectFirst("div.anime__details__pic")?.attr("data-setbg")
            ?: document.selectFirst("div.anime__details__pic img")?.attr("src")

        val details = document.selectFirst("div.anime__details__text") ?: document

        title = (
            details.selectFirst("div.anime__details__title > h3, div > h3")?.text()
                ?: document.selectFirst("h3")?.text().orEmpty()
        ).replace("Judul: ", "").trim()

        val infos = details.selectFirst("div.anime__details__widget")
        if (infos != null) {
            artist = infos.select("li:contains(Studio:) a").eachText().joinToString().takeUnless(String::isEmpty)
            status = parseStatus(infos.selectFirst("li:contains(Status:) a, li:contains(Status:)")?.text())

            genre = infos.select("li:contains(Genre:) a, li:contains(Tema:) a, li:contains(Demografis:) a")
                .eachText()
                .joinToString { it.trimEnd(',', ' ') }
                .takeUnless(String::isEmpty)
        }

        description = buildString {
            details.selectFirst("p#synopsisField, p.synopsis")?.text()?.also(::append)

            details.selectFirst("div.anime__details__title > span")?.text()
                ?.also { append("\n\nAlternative names: $it\n") }

            infos?.select("ul > li")?.eachText()?.forEach { append("\n$it") }
        }
    }

    private fun parseStatus(statusString: String?): Int = when {
        statusString == null -> SAnime.UNKNOWN
        statusString.contains("Sedang Tayang", ignoreCase = true) || statusString.contains("Ongoing", ignoreCase = true) -> SAnime.ONGOING
        statusString.contains("Selesai Tayang", ignoreCase = true) || statusString.contains("Completed", ignoreCase = true) -> SAnime.COMPLETED
        else -> SAnime.UNKNOWN
    }

    // ============================== Episodes ==============================
    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.useAsJsoup()

        val html = document.selectFirst(episodeListSelector())?.attr("data-content")
            ?: return emptyList()

        val newDoc = Jsoup.parse(html)
        val limits = newDoc.select("a.btn-secondary")

        return when {
            limits.isEmpty() -> {
                newDoc.select("a")
                    .filterNot { it.attr("href").contains("batch") }
                    .map(::episodeFromElement)
                    .reversed()
            }
            else -> {
                val (start, end) = limits.eachText().take(2).map {
                    it.filter(Char::isDigit).toIntOrNull() ?: 1
                }

                val location = document.location().substringBefore("?")

                (end downTo start).map { episodeNumber ->
                    SEpisode.create().apply {
                        name = "Ep $episodeNumber"
                        episode_number = episodeNumber.toFloat()
                        setUrlWithoutDomain("$location/episode/$episodeNumber")
                    }
                }
            }
        }
    }

    override fun episodeListSelector() = "a#episodeLists"

    override fun episodeFromElement(element: Element) = SEpisode.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        name = element.text()
        episode_number = name.filter(Char::isDigit).toFloatOrNull() ?: 1F
    }

    // ============================ Video Links =============================
    override fun videoListSelector() = "video#player > source"

    private val supportedHosters = listOf(
        "kuramadrive",
        "kuramadrive-v2",
        "filelions",
        "filemoon",
        "mega",
        "streamwish",
        "streamtape",
        "vidguard",
        "doodstream",
    )

    private val streamtapeExtractor by lazy { StreamTapeExtractor(client) }
    private val streamWishExtractor by lazy { StreamWishExtractor(client, headers) }
    private val filemoonExtractor by lazy { FilemoonExtractor(client) }
    private val vidguardExtractor by lazy { VidGuardExtractor(client) }
    private val doodExtractor by lazy { DoodExtractor(client) }

    override fun videoListParse(response: Response): List<Video> {
        val bodyStr = response.body.string()
        val doc = Jsoup.parse(bodyStr)

        val scriptData = getScriptData(bodyStr, doc) ?: return emptyList()

        val csrfToken = (
            doc.selectFirst("meta[name=csrf-token]")?.attr("content")
                ?: doc.selectFirst("meta[name=csrf-token]")?.attr("csrf-token")
        ) ?: return emptyList()

        val servers = doc.select("select#changeServer > option")
            .map { it.attr("value") to it.text().substringBefore(" (") }
            .filter { supportedHosters.contains(it.first) }

        val episodeUrl = response.request.url

        val reqHeaders = headersBuilder()
            .set("Referer", episodeUrl.toString())
            .set("X-Requested-With", "XMLHttpRequest")
            .build()

        val originalKuramadriveSources = doc.select("video#player > source").map {
            val src = it.attr("src")
            Video(src, "${it.attr("size")}p - kuramadrive", src)
        }.ifEmpty {
            val video = doc.selectFirst("video#player")
            val src = video?.attr("src") ?: video?.attr("data-hls-src")
            if (!src.isNullOrEmpty()) {
                listOf(Video(src, "kuramadrive", src))
            } else {
                emptyList()
            }
        }

        return servers.parallelCatchingFlatMapBlocking { (server, serverName) ->
            if (server == "kuramadrive" && originalKuramadriveSources.isNotEmpty()) {
                return@parallelCatchingFlatMapBlocking originalKuramadriveSources
            }

            val newHeaders = reqHeaders.newBuilder()
                .set("X-CSRF-TOKEN", csrfToken)
                .set("X-Fuck-ID", scriptData.tokenId)
                .set("X-Request-ID", getRandomString())
                .set("X-Request-Index", "0")
                .build()

            val hash = client.newCall(GET("$baseUrl/" + scriptData.authPath, newHeaders))
                .awaitSuccess()
                .bodyString()
                .trim('"')

            val newUrl = episodeUrl.newBuilder()
                .addQueryParameter("page", "1")
                .addQueryParameter(scriptData.tokenParam, hash)
                .addQueryParameter(scriptData.serverParam, server)
                .build()

            val requestBody = FormBody.Builder()
                .add("authorization", "kJuHHkaqcBFXiGMHQf6bJw8YAyDcwGD8Ur")
                .build()

            val playerDoc = client.newCall(POST(newUrl.toString(), newHeaders, requestBody))
                .awaitSuccess()
                .useAsJsoup()

            var url = playerDoc.selectFirst("div.video-content iframe, iframe")?.attr("src")
            if (url != null && url.startsWith("/")) {
                url = "$baseUrl$url"
            }

            if (url != null && url.contains("/stream")) {
                runCatching {
                    val streamDoc = client.newCall(GET(url!!, reqHeaders)).execute().useAsJsoup()
                    url = streamDoc.selectFirst("iframe")?.attr("src") ?: url
                }
            }

            when (server) {
                "filelions" if url != null -> streamWishExtractor.videosFromUrl(url!!)
                "filemoon" if url != null -> filemoonExtractor.videosFromUrl(url!!)
                "streamwish" if url != null -> streamWishExtractor.videosFromUrl(url!!)
                "streamtape" if url != null -> streamtapeExtractor.videosFromUrl(url!!)
                "vidguard" if url != null -> vidguardExtractor.videosFromUrl(url!!)
                "doodstream" if url != null -> doodExtractor.videosFromUrl(url!!)
                else -> {
                    val sources = playerDoc.select("video#player > source")
                    if (sources.isNotEmpty()) {
                        sources.map {
                            val src = it.attr("src")
                            Video(src, "${it.attr("size")}p - $serverName", src)
                        }
                    } else {
                        val video = playerDoc.selectFirst("video#player")
                        val src = video?.attr("src") ?: video?.attr("data-hls-src")
                        if (!src.isNullOrEmpty()) {
                            listOf(Video(src, serverName, src))
                        } else {
                            emptyList()
                        }
                    }
                }
            }
        }
    }

    private fun getScriptData(html: String, doc: Document): ScriptDataDto? {
        return runCatching {
            val processEnvRegex = Regex("""window\.process\s*=\s*\{[\s\S]*?env:\s*\{([\s\S]*?)\}[\s\S]*?\}""")
            val envMatch = processEnvRegex.find(html)

            val envContent = if (envMatch != null) {
                envMatch.groupValues[1]
            } else {
                val sizzlybUrl = "$baseUrl/assets/js/sizzlyb.js"
                val sizzlybRes = client.newCall(GET(sizzlybUrl, headers)).execute()
                if (!sizzlybRes.isSuccessful) return@runCatching null

                val sizzlybStr = sizzlybRes.body.string()
                val attrMatch = Regex("""MIX_JS_ROUTE_PARAM_ATTR:\s*["']([^"']+)["']""").find(sizzlybStr)
                val attrName = attrMatch?.groupValues?.get(1) ?: return@runCatching null

                val scriptId = doc.selectFirst("[$attrName]")?.attr(attrName) ?: return@runCatching null

                val varJsUrl = "$baseUrl/assets/js/$scriptId.js"
                val varJsRes = client.newCall(GET(varJsUrl, headers)).execute()
                if (!varJsRes.isSuccessful) return@runCatching null

                varJsRes.body.string()
            }

            val envVars = mutableMapOf<String, String>()
            val varRegex = Regex("""(\w+):\s*['"]([^'"]+)['"]""")

            varRegex.findAll(envContent).forEach { match ->
                val key = match.groupValues[1]
                val value = match.groupValues[2]
                envVars[key] = value
            }

            ScriptDataDto(
                authPathPrefix = envVars["MIX_PREFIX_AUTH_ROUTE_PARAM"] ?: "",
                authPathSuffix = envVars["MIX_AUTH_ROUTE_PARAM"] ?: "",
                authKey = envVars["MIX_AUTH_KEY"] ?: "",
                authToken = envVars["MIX_AUTH_TOKEN"] ?: "",
                tokenParam = envVars["MIX_PAGE_TOKEN_KEY"] ?: "",
                serverParam = envVars["MIX_STREAM_SERVER_KEY"] ?: "",
            )
        }.getOrNull()
    }

    @Serializable
    internal data class ScriptDataDto(
        @SerialName("MIX_PREFIX_AUTH_ROUTE_PARAM")
        private val authPathPrefix: String,

        @SerialName("MIX_AUTH_ROUTE_PARAM")
        private val authPathSuffix: String,

        @SerialName("MIX_AUTH_KEY") private val authKey: String,
        @SerialName("MIX_AUTH_TOKEN") private val authToken: String,

        @SerialName("MIX_PAGE_TOKEN_KEY") val tokenParam: String,
        @SerialName("MIX_STREAM_SERVER_KEY") val serverParam: String,
    ) {
        val authPath = "$authPathPrefix$authPathSuffix"
        val tokenId = "$authKey:$authToken"
    }

    private fun getRandomString(length: Int = 8): String {
        val allowedChars = ('a'..'z') + ('0'..'9')
        return (1..length)
            .map { allowedChars.random() }
            .joinToString("")
    }

    override fun List<Video>.sortVideos(): List<Video> {
        val quality = preferences.getString(PREF_QUALITY_KEY, PREF_QUALITY_DEFAULT)!!

        return sortedWith(
            compareBy { it.videoTitle.contains(quality) },
        ).reversed()
    }

    override fun videoFromElement(element: Element) = throw UnsupportedOperationException()

    // ============================== Filters ===============================
    override fun getFilterList() = KuramanimeFilters.FILTER_LIST

    // ============================== Settings ==============================
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_QUALITY_KEY
            title = PREF_QUALITY_TITLE
            entries = PREF_QUALITY_ENTRIES
            entryValues = PREF_QUALITY_VALUES
            setDefaultValue(PREF_QUALITY_DEFAULT)
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                val entry = entryValues[index] as String
                preferences.edit().putString(key, entry).commit()
            }
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PREF_BASE_URL_KEY
            title = "Override Base URL"
            dialogTitle = "Override Base URL"
            summary = "Ganti URL Kuramanime jika domain website berganti (Default: $DEFAULT_BASE_URL)"
            setDefaultValue(DEFAULT_BASE_URL)

            setOnPreferenceChangeListener { _, newValue ->
                val newUrl = (newValue as String).trim().trimEnd('/')
                preferences.edit().putString(key, newUrl).commit()
            }
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_USE_SCRAPER_KEY
            title = "Gunakan Scraper API Fallback"
            summary = "Otomatis bypass proteksi jika request langsung terhalang"
            setDefaultValue(true)

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putBoolean(key, newValue as Boolean).commit()
            }
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PREF_SCRAPER_URL_KEY
            title = "Scraper API URL"
            dialogTitle = "Scraper API URL"
            summary = "Endpoint Scraper API (Default: $DEFAULT_SCRAPER_URL)"
            setDefaultValue(DEFAULT_SCRAPER_URL)

            setOnPreferenceChangeListener { _, newValue ->
                val newUrl = (newValue as String).trim().trimEnd('/')
                preferences.edit().putString(key, newUrl).commit()
            }
        }.also(screen::addPreference)
    }

    /**
     * Interceptor to gracefully fallback to external Scraper API (e.g. https://rbot.duar.eu.cc)
     * if direct request gets HTTP 403 / 503 / challenge.
     */
    private class ScraperFallbackInterceptor(
        private val preferences: SharedPreferences,
        private val json: Json,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val useScraper = preferences.getBoolean(PREF_USE_SCRAPER_KEY, true)
            val scraperApi = preferences.getString(PREF_SCRAPER_URL_KEY, DEFAULT_SCRAPER_URL)!!.trimEnd('/')

            val host = request.url.host
            if (!useScraper || host.contains("duar.eu.cc") || request.url.encodedPath.contains("cf-clearance-scraper")) {
                return chain.proceed(request)
            }

            var directResponse: Response? = null
            var needFallback = false

            try {
                val res = chain.proceed(request)
                if (res.code in listOf(403, 503) || res.header("cf-mitigated") != null) {
                    needFallback = true
                    res.close()
                } else {
                    directResponse = res
                }
            } catch (e: Exception) {
                needFallback = true
            }

            if (directResponse != null) {
                return directResponse
            }

            if (needFallback && request.method == "GET") {
                try {
                    val jsonPayload = """{"url":"${request.url}","mode":"source"}"""
                    val scraperRequest = Request.Builder()
                        .url("$scraperApi/cf-clearance-scraper")
                        .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                        .header("Content-Type", "application/json")
                        .build()

                    val scraperResponse = chain.proceed(scraperRequest)
                    if (scraperResponse.isSuccessful) {
                        val bodyText = scraperResponse.body.string()
                        val htmlContent = runCatching {
                            val parsedJson = json.parseToJsonElement(bodyText).jsonObject
                            parsedJson["source"]?.jsonPrimitive?.content ?: bodyText
                        }.getOrDefault(bodyText)

                        return Response.Builder()
                            .request(request)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK (via Scraper)")
                            .body(htmlContent.toResponseBody("text/html; charset=utf-8".toMediaType()))
                            .build()
                    }
                } catch (_: Exception) {}
            }

            return chain.proceed(request)
        }
    }

    companion object {
        private const val DEFAULT_BASE_URL = "https://v20.kuramanime.ing"
        private const val PREF_BASE_URL_KEY = "pref_override_base_url"

        private const val DEFAULT_SCRAPER_URL = "https://rbot.duar.eu.cc"
        private const val PREF_USE_SCRAPER_KEY = "pref_use_scraper_fallback"
        private const val PREF_SCRAPER_URL_KEY = "pref_scraper_api_url"

        private const val PREF_QUALITY_KEY = "preferred_quality"
        private const val PREF_QUALITY_TITLE = "Preferred quality"
        private const val PREF_QUALITY_DEFAULT = "1080p"
        private val PREF_QUALITY_ENTRIES = arrayOf("1080p", "720p", "480p", "360p")
        private val PREF_QUALITY_VALUES = PREF_QUALITY_ENTRIES
    }
}
