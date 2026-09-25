package eu.kanade.tachiyomi.animeextension.id.astronime

import android.util.Base64
import aniyomi.lib.bloggerextractor.BloggerExtractor
import aniyomi.lib.mp4uploadextractor.Mp4uploadExtractor
import aniyomi.lib.pixeldrainextractor.PixelDrainExtractor
import aniyomi.lib.streamtapeextractor.StreamTapeExtractor
import aniyomi.lib.streamwishextractor.StreamWishExtractor
import aniyomi.lib.vidhideextractor.VidHideExtractor
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import keiyoushi.utils.ParsedAnimeHttpLegacySource
import keiyoushi.utils.parallelCatchingFlatMapBlocking
import keiyoushi.utils.tryParse
import keiyoushi.utils.useAsJsoup
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class Astronime : ParsedAnimeHttpLegacySource() {
    override val name: String = "Astronime"

    override val baseUrl: String = "https://astronime.id"

    override val lang: String = "id"

    override val supportsLatest: Boolean = true

    private val solverClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build()
    }

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val response = try {
                chain.proceed(request)
            } catch (e: Exception) {
                null
            }

            val isBlocked = if (response != null) {
                val code = response.code
                if (code in listOf(403, 503, 520, 521, 522, 524)) {
                    true
                } else if (response.isSuccessful) {
                    val peek = runCatching { response.peekBody(1024).string() }.getOrDefault("")
                    peek.contains("Just a moment...", ignoreCase = true) ||
                        peek.contains("Attention Required! | Cloudflare", ignoreCase = true) ||
                        peek.contains("challenge-running", ignoreCase = true)
                } else false
            } else true

            if (isBlocked && request.url.host.contains("astronime", ignoreCase = true)) {
                try {
                    val payload = JSONObject().apply {
                        put("url", request.url.toString())
                        put("mode", "source")
                    }.toString()
                    val reqBody = payload.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                    val solverReq = Request.Builder()
                        .url("https://rbot.duar.eu.cc/cf-clearance-scraper")
                        .post(reqBody)
                        .header("Content-Type", "application/json")
                        .build()

                    val solverResp = solverClient.newCall(solverReq).execute()
                    val bodyStr = solverResp.body?.string().orEmpty()
                    val json = JSONObject(bodyStr)
                    val htmlSource = json.optString("source")
                    val resCode = json.optInt("code", 200)

                    if (htmlSource.isNotBlank() && resCode in 200..299) {
                        response?.close()
                        return@addInterceptor Response.Builder()
                            .request(request)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK (via Cloudflare Solver)")
                            .headers(response?.headers ?: Headers.Builder().build())
                            .body(htmlSource.toResponseBody("text/html; charset=UTF-8".toMediaTypeOrNull()))
                            .build()
                    }
                } catch (_: Exception) {}
            }

            response ?: throw java.io.IOException("Request failed to ${request.url}")
        }
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
        .add("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")

    // ============================== Popular ===============================
    override fun popularAnimeRequest(page: Int): Request =
        if (page == 1) GET("$baseUrl/anime/?order=popular", headers) else GET("$baseUrl/anime/page/$page/?order=popular", headers)

    override fun popularAnimeSelector(): String = "article.animpost, article.anime, div.animpost, div.animepost, div.listupd article.bsx, div.listupd div.bsx, div.listupd article, article.bsx, div.bsx, article"

    override fun popularAnimeFromElement(element: Element): SAnime = SAnime.create().apply {
        val link = if (element.tagName().lowercase() == "a") element else element.selectFirst("a[href*=/anime/], a[href], h2 a, h4 a") ?: return@apply
        setUrlWithoutDomain(link.attr("href"))
        title = element.selectFirst("div.data h2, div.data h4, a[title], a[alt], img[alt], .tt h2, .tt, h2, h3, h4, a")?.text()?.trim()
            ?.ifBlank { link.attr("title").ifBlank { link.attr("alt") } } ?: link.attr("title").trim()
        thumbnail_url = element.getImageUrl()
    }

    override fun popularAnimeNextPageSelector(): String? = "div.pagination a.next, a.next, nav.pagination a.next"

    override fun popularAnimeParse(response: Response): AnimesPage {
        val document = response.useAsJsoup()
        val anime = document.select(popularAnimeSelector())
            .mapNotNull { runCatching { popularAnimeFromElement(it) }.getOrNull() }
            .filter { it.title.isNotBlank() && it.url.isNotBlank() }
            .distinctBy { it.url.trim().removeSuffix("/") }
        val hasNextPage = popularAnimeNextPageSelector()?.let { document.selectFirst(it) != null } ?: false
        return AnimesPage(anime, hasNextPage)
    }

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request =
        if (page == 1) GET("$baseUrl/anime/?order=update", headers) else GET("$baseUrl/anime/page/$page/?order=update", headers)

    override fun latestUpdatesSelector(): String = popularAnimeSelector()

    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector(): String? = popularAnimeNextPageSelector()

    override fun latestUpdatesParse(response: Response): AnimesPage {
        val document = response.useAsJsoup()
        val anime = document.select(latestUpdatesSelector())
            .mapNotNull { runCatching { latestUpdatesFromElement(it) }.getOrNull() }
            .filter { it.title.isNotBlank() && it.url.isNotBlank() }
            .distinctBy { it.url.trim().removeSuffix("/") }
        val hasNextPage = latestUpdatesNextPageSelector()?.let { document.selectFirst(it) != null } ?: false
        return AnimesPage(anime, hasNextPage)
    }

    // =============================== Search ===============================
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        return if (query.isNotBlank()) {
            if (page == 1) GET("$baseUrl/?s=$query", headers) else GET("$baseUrl/page/$page/?s=$query", headers)
        } else {
            val params = AstronimeFilters.getSearchParameters(filters)
            if (page == 1) GET("$baseUrl/anime/?$params", headers) else GET("$baseUrl/anime/page/$page/?$params", headers)
        }
    }

    override fun searchAnimeSelector(): String = popularAnimeSelector()

    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun searchAnimeNextPageSelector(): String? = popularAnimeNextPageSelector()

    override fun searchAnimeParse(response: Response): AnimesPage {
        val document = response.useAsJsoup()
        val anime = document.select(searchAnimeSelector())
            .mapNotNull { runCatching { searchAnimeFromElement(it) }.getOrNull() }
            .filter { it.title.isNotBlank() && it.url.isNotBlank() }
            .distinctBy { it.url.trim().removeSuffix("/") }
        val hasNextPage = searchAnimeNextPageSelector()?.let { document.selectFirst(it) != null } ?: false
        return AnimesPage(anime, hasNextPage)
    }

    override fun getFilterList(): AnimeFilterList = AstronimeFilters.FILTER_LIST

    // =========================== Anime Details ============================
    override fun animeDetailsParse(document: Document): SAnime = SAnime.create().apply {
        title = document.selectFirst("h1.entry-title, .entry-title, h1")?.text()?.replace("Trailer", "", ignoreCase = true)?.trim().orEmpty()
        thumbnail_url = document.selectFirst("div.thumb img, div.bigcontent img, .post-thumbnail img, div.limage img, .thumb img, .thumb")?.getImageUrl()

        val statusText = document.selectFirst("div.info-content span:contains(Status), div.spe span:contains(Status)")?.text().orEmpty()
        status = when {
            statusText.contains("Completed", ignoreCase = true) -> SAnime.COMPLETED
            statusText.contains("Ongoing", ignoreCase = true) -> SAnime.ONGOING
            else -> SAnime.UNKNOWN
        }

        val studio = document.selectFirst("div.info-content span:contains(Studio), div.spe span:contains(Studio)")?.text()?.substringAfter(":")?.trim().orEmpty()
        author = studio

        genre = document.select("div.genxed a, .genre-info a, .genres a").eachText().joinToString()

        val synopsis = document.selectFirst("div.entry-content[itemprop='description'], div.entry-content, div.desc")?.text()?.trim().orEmpty()
        val type = document.selectFirst("div.info-content span:contains(Tipe), div.spe span:contains(Tipe)")?.text()?.substringAfter(":")?.trim().orEmpty()
        val totalEp = document.selectFirst("div.info-content span:contains(Episode), div.spe span:contains(Episode)")?.text()?.substringAfter(":")?.trim().orEmpty()
        val duration = document.selectFirst("div.info-content span:contains(Durasi), div.spe span:contains(Durasi)")?.text()?.substringAfter(":")?.trim().orEmpty()

        description = buildString {
            if (synopsis.isNotBlank()) append("$synopsis\n\n")
            if (type.isNotBlank()) append("Tipe: $type\n")
            if (totalEp.isNotBlank()) append("Episode: $totalEp\n")
            if (duration.isNotBlank()) append("Durasi: $duration\n")
            if (studio.isNotBlank()) append("Studio: $studio\n")
        }.trim()

        initialized = true
    }

    // ============================== Episodes ==============================
    override fun episodeListSelector(): String = "div.eplister ul li, ul.clstyle li, div.episodelist ul li"

    private val episodePattern = Regex("""(?i)(?:Episode|Ep|Eps)\s*(\d+(?:\.\d+)?)""")
    private val dateFormatter = SimpleDateFormat("MMMM d, yyyy", Locale("id", "ID"))
    private val dateFormatterEn = SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH)

    override fun episodeFromElement(element: Element): SEpisode = SEpisode.create().apply {
        val link = element.selectFirst("a") ?: throw Exception("Missing episode link")
        setUrlWithoutDomain(link.attr("href"))

        val title = element.selectFirst(".epl-title, .title")?.text()?.trim() ?: link.text().trim()
        name = title

        val numText = element.selectFirst(".epl-num, .epnum")?.text()?.trim().orEmpty()
        val numMatch = episodePattern.find(title) ?: Regex("""(\d+(?:\.\d+)?)""").find(numText)
        episode_number = numMatch?.groupValues?.getOrNull(1)?.toFloatOrNull()
            ?: numText.toFloatOrNull()
            ?: 1F

        val dateStr = element.selectFirst(".epl-date, .date")?.text()?.trim()
        date_upload = dateStr?.let {
            dateFormatter.tryParse(it) ?: dateFormatterEn.tryParse(it)
        } ?: 0L
    }

    // ============================ Video Links =============================
    override fun videoListSelector(): String = throw UnsupportedOperationException()
    override fun videoFromElement(element: Element): Video = throw UnsupportedOperationException()

    override fun videoListParse(response: Response): List<Video> {
        val doc = response.useAsJsoup()
        val optionElements = doc.select("select.mirror option, div.mirror option, .mirror option")

        val mirrorOptions = optionElements.mapNotNull { opt ->
            val name = opt.text().trim()
            val b64 = opt.attr("value").trim()
            if (b64.isNotBlank() && !name.contains("Pilih", ignoreCase = true)) {
                val decoded = b64.b64Decode()
                val iframeSrc = Regex("""src\s*=\s*["']([^"']+)["']""").find(decoded)?.groupValues?.get(1)
                    ?: if (decoded.startsWith("http")) decoded else null
                if (!iframeSrc.isNullOrBlank()) Pair(name, iframeSrc) else null
            } else null
        }

        val iframeElements = doc.select("iframe, div.player-embed iframe, div#embed_holder iframe, div.responsive-embed-stream iframe, .video-content iframe")
        val iframes = iframeElements.mapNotNull {
            val src = it.attr("src").ifEmpty { it.attr("data-src") }.trim()
            if (src.startsWith("http") && !src.contains("acceptable.a-ads") && !src.contains("facebook.com")) {
                Pair("Default", src)
            } else null
        }

        val allServers = (mirrorOptions + iframes).distinctBy { it.second }

        return allServers.parallelCatchingFlatMapBlocking { server ->
            getVideosFromEmbed(server.first, server.second)
        }.distinctBy { it.videoUrl }
    }

    private fun String.b64Decode(): String = try {
        String(Base64.decode(this, Base64.DEFAULT), Charsets.UTF_8)
    } catch (e: Exception) {
        this
    }

    private val mp4uploadExtractor by lazy { Mp4uploadExtractor(client) }
    private val bloggerExtractor by lazy { BloggerExtractor(client) }
    private val pixelDrainExtractor by lazy { PixelDrainExtractor() }
    private val streamTapeExtractor by lazy { StreamTapeExtractor(client) }

    private suspend fun getVideosFromEmbed(server: String, link: String): List<Video> {
        if (link.isBlank()) return emptyList()

        val cleanHeaders = Headers.Builder()
            .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build()

        return runCatching {
            when {
                // Mp4Upload
                "mp4upload" in link -> {
                    val mp4Headers = Headers.Builder()
                        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .add("Referer", "https://www.mp4upload.com/")
                        .add("Accept", "*/*")
                        .build()
                    mp4uploadExtractor.videosFromUrl(link, mp4Headers)
                }

                // VidHide
                "vidhide" in link || "streamhide" in link || "odvidhide" in link || "vidlion" in link -> {
                    VidHideExtractor(client, cleanHeaders).videosFromUrl(link)
                }

                // StreamWish / FileLions
                "streamwish" in link || "filelions" in link || "wishembed" in link || "wishfast" in link -> {
                    StreamWishExtractor(client, cleanHeaders).videosFromUrl(link, videoNameGen = { "$server - $it" })
                }

                // StreamTape
                "streamtape" in link || "streamta.pe" in link -> {
                    streamTapeExtractor.videoFromUrl(link)?.let(::listOf).orEmpty()
                }

                // Pixeldrain
                "pixeldrain" in link -> {
                    val id = Regex("""/(?:u|file|api/file)/([a-zA-Z0-9]+)""").find(link)?.groupValues?.get(1)
                    if (!id.isNullOrBlank()) {
                        val dlUrl = "https://pixeldrain.com/api/file/$id?download"
                        listOf(Video(dlUrl, "$server (PixelDrain)", headers = cleanHeaders))
                    } else {
                        pixelDrainExtractor.videosFromUrl(link, "$server - ")
                    }
                }

                // Blogger
                "blogger" in link || "bp.blogspot" in link || "video.googleusercontent" in link -> {
                    val bloggerHeaders = Headers.Builder()
                        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .add("Referer", "https://www.blogger.com/")
                        .add("Accept", "*/*")
                        .build()
                    bloggerExtractor.videosFromUrl(link, bloggerHeaders, server)
                }

                link.endsWith(".mp4") || link.endsWith(".m3u8") || link.contains(".mp4?") || link.contains(".m3u8?") -> {
                    listOf(Video(link, server, headers = cleanHeaders))
                }

                else -> emptyList()
            }
        }.getOrDefault(emptyList())
    }

    private fun Element.getImageUrl(): String? {
        val img = if (tagName().lowercase() == "img") this else selectFirst("img")
        val candidate = if (img != null) {
            val attrList = listOf(
                "data-src",
                "data-lazy-src",
                "data-original",
                "data-cfsrc",
                "data-srcset",
                "srcset",
                "src",
            )
            var found: String? = null
            for (attr in attrList) {
                val v = if (img.hasAttr(attr)) img.attr("abs:$attr").ifBlank { img.attr(attr) } else ""
                val clean = if (attr.contains("srcset")) v.substringBefore(" ").substringBefore(",") else v
                if (clean.isNotBlank() && !clean.startsWith("data:image", ignoreCase = true)) {
                    found = clean.trim()
                    break
                }
            }
            found
        } else {
            val style = attr("style")
            if ("url(" in style) {
                Regex("""url\(['"]?([^'"]+)['"]?\)""").find(style)?.groupValues?.get(1)
            } else null
        }

        if (candidate.isNullOrBlank() || candidate.startsWith("data:image", ignoreCase = true)) return null

        val url = when {
            candidate.startsWith("//") -> "https:$candidate"
            candidate.startsWith("/") -> "$baseUrl$candidate"
            else -> candidate
        }

        return url.substringBefore("?resize")
    }
}

