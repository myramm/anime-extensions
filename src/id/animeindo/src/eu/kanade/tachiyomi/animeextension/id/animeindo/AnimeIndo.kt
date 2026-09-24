package eu.kanade.tachiyomi.animeextension.id.animeindo

import android.util.Base64
import android.util.Log
import aniyomi.lib.bloggerextractor.BloggerExtractor
import aniyomi.lib.doodextractor.DoodExtractor
import aniyomi.lib.gdriveplayerextractor.GdrivePlayerExtractor
import aniyomi.lib.mp4uploadextractor.Mp4uploadExtractor
import aniyomi.lib.okruextractor.OkruExtractor
import aniyomi.lib.pixeldrainextractor.PixelDrainExtractor
import aniyomi.lib.playlistutils.PlaylistUtils
import aniyomi.lib.streamtapeextractor.StreamTapeExtractor
import aniyomi.lib.streamwishextractor.StreamWishExtractor
import aniyomi.lib.vidhideextractor.VidHideExtractor
import aniyomi.lib.youruploadextractor.YourUploadExtractor
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import keiyoushi.lib.jsunpacker.JsUnpacker
import keiyoushi.utils.ParsedAnimeHttpLegacySource
import keiyoushi.utils.parallelCatchingFlatMapBlocking
import keiyoushi.utils.useAsJsoup
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class AnimeIndo : ParsedAnimeHttpLegacySource() {

    override val name = "AnimeIndo"

    override val baseUrl = "https://animeindo.skin"

    override val lang = "id"

    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        .add("Referer", "$baseUrl/")
        .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")

    // ============================== Popular ===============================
    override fun popularAnimeRequest(page: Int): Request =
        GET("$baseUrl/trending?page=$page", headers)

    override fun popularAnimeSelector(): String =
        "div.relative.group:has(a[href*=/tv-show/], a[href*=/movie/]), a[href*=/tv-show/], a[href*=/movie/]"

    override fun popularAnimeFromElement(element: Element): SAnime = SAnime.create().apply {
        val link = if (element.tagName().lowercase() == "a") element else element.selectFirst("a[href*=/tv-show/], a[href*=/movie/]")
            ?: throw Exception("No link")
        val href = link.attr("href").trim()
        setUrlWithoutDomain(href)

        val img = element.selectFirst("img")
        title = img?.attr("alt")?.trim()
            ?.ifEmpty { link.selectFirst("h2, h3, div.font-bold, .title")?.text()?.trim() }
            ?: link.text().trim()

        thumbnail_url = img?.attr("data-src")?.ifEmpty { img.attr("src") }
            ?.ifEmpty { img.attr("data-lazy-src") }
            ?: ""
    }

    override fun popularAnimeNextPageSelector(): String? =
        "nav.pagination a[rel=next], a:contains(Next), nav[role=navigation] a:has(svg:last-child)"

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/browse?page=$page", headers)

    override fun latestUpdatesSelector(): String = popularAnimeSelector()

    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector(): String? = popularAnimeNextPageSelector()

    // =============================== Search ===============================
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val urlBuilder = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("browse")
            addQueryParameter("page", page.toString())
            if (query.isNotBlank()) {
                addQueryParameter("q", query)
            }
        }

        filters.forEach { filter ->
            when (filter) {
                is AnimeIndoFilters.TypeFilter -> {
                    val part = filter.toUriPart()
                    if (part.isNotBlank()) urlBuilder.addQueryParameter("type", part)
                }
                is AnimeIndoFilters.QualityFilter -> {
                    val part = filter.toUriPart()
                    if (part.isNotBlank()) urlBuilder.addQueryParameter("quality", part)
                }
                is AnimeIndoFilters.ReleaseFilter -> {
                    val part = filter.toUriPart()
                    if (part.isNotBlank()) urlBuilder.addQueryParameter("release", part)
                }
                is AnimeIndoFilters.GenreFilter -> {
                    filter.state.filter { it.state }.forEach {
                        urlBuilder.addQueryParameter("genre[]", it.value)
                    }
                }
                is AnimeIndoFilters.CountryFilter -> {
                    filter.state.filter { it.state }.forEach {
                        urlBuilder.addQueryParameter("country[]", it.value)
                    }
                }
                else -> {}
            }
        }

        return GET(urlBuilder.build().toString(), headers)
    }

    override fun searchAnimeSelector(): String = popularAnimeSelector()

    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun searchAnimeNextPageSelector(): String? = popularAnimeNextPageSelector()

    // ============================== Filters ===============================
    override fun getFilterList(): AnimeFilterList = AnimeIndoFilters.getFilterList()

    // =========================== Anime Details ============================
    override fun animeDetailsParse(document: Document): SAnime = SAnime.create().apply {
        title = document.selectFirst("h1")?.text()?.trim()
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")
            ?: "Anime"

        thumbnail_url = document.selectFirst("picture img, div.thumb img, div.poster img, img.lazyload")
            ?.let { it.attr("data-src").ifEmpty { it.attr("src") }.ifEmpty { it.attr("data-lazy-src") } }
            ?: document.selectFirst("meta[property=og:image]")?.attr("content")

        val descElement = document.selectFirst("p.leading-relaxed, div[class*=synopsis], div[class*=description], div.text-gray-300")
        description = descElement?.text()?.trim()

        genre = document.select("a[href*=/genre/], a[href*=/genres/]").joinToString { it.text().trim() }

        val fullText = document.text().lowercase()
        status = when {
            "completed" in fullText || "selesai" in fullText -> SAnime.COMPLETED
            "ongoing" in fullText || "airing" in fullText || "berjalan" in fullText -> SAnime.ONGOING
            else -> SAnime.UNKNOWN
        }
    }

    // ============================== Episodes ==============================
    override fun episodeListSelector(): String = "a[href*=/episode/], a[href*=/watch/]"

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.useAsJsoup()
        val episodeElements = document.select(episodeListSelector())

        return episodeElements.mapNotNull { element ->
            runCatching {
                val href = element.attr("href").trim()
                if (href.isBlank()) return@runCatching null

                val fullText = element.text().trim()
                val match = Regex("""(?:Episode|Ep|Eps)?\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE).find(fullText)
                    ?: Regex("""/(\d+)-(\d+)""").find(href)

                val epNum = if (match?.groupValues?.size ?: 0 > 2) {
                    match?.groupValues?.get(2)?.toFloatOrNull() ?: 1F
                } else {
                    match?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: 1F
                }

                SEpisode.create().apply {
                    setUrlWithoutDomain(href)
                    name = if (fullText.isNotBlank() && !fullText.matches(Regex("""^\d+(\.\d+)?$"""))) {
                        fullText
                    } else {
                        "Episode ${epNum.toInt()}"
                    }
                    episode_number = epNum
                }
            }.getOrNull()
        }.distinctBy { it.url }.reversed()
    }

    override fun episodeFromElement(element: Element): SEpisode = throw UnsupportedOperationException()

    // ============================ Video Links =============================
    override fun videoListSelector(): String = throw UnsupportedOperationException()
    override fun videoFromElement(element: Element): Video = throw UnsupportedOperationException()

    private val mp4uploadExtractor by lazy { Mp4uploadExtractor(client) }
    private val gdrivePlayerExtractor by lazy { GdrivePlayerExtractor(client) }
    private val streamTapeExtractor by lazy { StreamTapeExtractor(client) }
    private val yourUploadExtractor by lazy { YourUploadExtractor(client) }
    private val okruExtractor by lazy { OkruExtractor(client) }
    private val bloggerExtractor by lazy { BloggerExtractor(client) }
    private val doodExtractor by lazy { DoodExtractor(client) }
    private val pixelDrainExtractor by lazy { PixelDrainExtractor() }
    private val playlistUtils by lazy { PlaylistUtils(client, headers) }

    override fun videoListParse(response: Response): List<Video> {
        val document = response.useAsJsoup()
        val embedServers = mutableListOf<Pair<String, String>>()

        // 1. Livewire 3 snapshot JSON extraction
        val wireDiv = document.selectFirst("div[wire\\:snapshot]")
        if (wireDiv != null) {
            val snapshotStr = wireDiv.attr("wire:snapshot")
            if (snapshotStr.isNotBlank()) {
                runCatching {
                    val snapshotJson = JSONObject(snapshotStr)
                    val dataJson = snapshotJson.optJSONObject("data")
                    val videosArray = dataJson?.optJSONArray("videos")
                    if (videosArray != null) {
                        extractEmbedItems(videosArray, embedServers)
                    }
                }
            }
        }

        // 2. Direct iframes / embeds on page
        document.select("iframe[src], iframe[data-src], div.player iframe").forEach { iframe ->
            val src = iframe.attr("src").ifEmpty { iframe.attr("data-src") }.trim()
            if (src.isNotBlank() && src.startsWith("http")) {
                embedServers.add(Pair(src, "Server"))
            }
        }

        val allServers = embedServers.distinctBy { it.first }

        val videos: List<Video> = allServers.parallelCatchingFlatMapBlocking { server ->
            getVideoList(server.first, server.second)
        }

        return videos.distinctBy { it.videoUrl }
    }

    private fun extractEmbedItems(obj: Any?, list: MutableList<Pair<String, String>>) {
        when (obj) {
            is JSONArray -> {
                for (i in 0 until obj.length()) {
                    extractEmbedItems(obj.get(i), list)
                }
            }
            is JSONObject -> {
                val link = obj.optString("link")
                val label = obj.optString("label", "Server")
                if (link.isNotBlank() && link.startsWith("http")) {
                    list.add(Pair(link, label))
                }
            }
        }
    }

    private suspend fun getVideoList(url: String, name: String): List<Video> {
        if (url.isBlank()) return emptyList()
        val lowerName = name.lowercase()
        val lowerUrl = url.lowercase()

        val cleanHeaders = Headers.Builder()
            .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build()

        return runCatching {
            when {
                // Blogger / Google UserContent streams
                "blogger" in lowerName || "blogger" in lowerUrl || "bp.blogspot" in lowerUrl || "video.googleusercontent" in lowerUrl -> {
                    val bloggerHeaders = Headers.Builder()
                        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .add("Referer", "https://www.blogger.com/")
                        .add("Accept", "*/*")
                        .build()
                    bloggerExtractor.videosFromUrl(url, bloggerHeaders, name)
                }

                // VidHide
                "vidhide" in lowerName || "vidhide" in lowerUrl || "streamhide" in lowerUrl || "odvidhide" in lowerUrl || "vidlion" in lowerUrl || "fl" == lowerName -> {
                    VidHideExtractor(client, cleanHeaders).videosFromUrl(url)
                }

                // StreamWish
                "streamwish" in lowerName || "streamwish" in lowerUrl || "filelions" in lowerUrl || "wishembed" in lowerUrl || "obeywish" in lowerUrl || "sw" == lowerName -> {
                    StreamWishExtractor(client, cleanHeaders).videosFromUrl(url, videoNameGen = { if (name.isNotBlank()) "$name - $it" else it })
                }

                // Mp4Upload
                "mp4upload" in lowerName || "mp4upload" in lowerUrl -> {
                    val mp4Headers = Headers.Builder()
                        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .add("Referer", "https://www.mp4upload.com/")
                        .add("Accept", "*/*")
                        .build()
                    mp4uploadExtractor.videosFromUrl(url, mp4Headers, suffix = name)
                }

                // YourUpload
                "yourupload" in lowerName || "yourupload" in lowerUrl -> {
                    val youruploadHeaders = Headers.Builder()
                        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .add("Referer", "https://www.yourupload.com/")
                        .add("Accept", "*/*")
                        .build()
                    yourUploadExtractor.videoFromUrl(url, youruploadHeaders, name)
                }

                // StreamTape
                "streamtape" in lowerName || "streamtape" in lowerUrl || "streamta.pe" in lowerUrl || "tapewith" in lowerUrl || "st" == lowerName -> {
                    streamTapeExtractor.videoFromUrl(url)?.let(::listOf).orEmpty()
                }

                // Ok.ru
                "ok.ru" in lowerUrl || "okru" in lowerName -> {
                    okruExtractor.videosFromUrl(url)
                }

                // Pixeldrain
                "pixeldrain" in lowerName || "pixeldrain" in lowerUrl -> {
                    val id = Regex("""/(?:u|file|api/file)/([a-zA-Z0-9]+)""").find(url)?.groupValues?.get(1)
                    if (!id.isNullOrBlank()) {
                        val dlUrl = "https://pixeldrain.com/api/file/$id?download"
                        listOf(Video(dlUrl, if (name.isNotBlank()) name else "PixelDrain", headers = cleanHeaders))
                    } else {
                        pixelDrainExtractor.videosFromUrl(url, prefix = if (name.isNotBlank()) "$name - " else "")
                    }
                }

                // DoodStream
                "dood" in lowerName || "dood" in lowerUrl -> {
                    doodExtractor.videosFromUrl(url)
                }

                // Direct video link (.mp4 / .m3u8)
                url.contains(".m3u8") -> {
                    playlistUtils.extractFromHls(url, referer = "$baseUrl/", videoNameGen = { if (name.isNotBlank()) "$name - $it" else it })
                }
                url.endsWith(".mp4") || url.contains(".mp4?") -> {
                    val streamHeaders = Headers.Builder()
                        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .add("Accept", "*/*")
                        .build()
                    listOf(Video(url, if (name.isNotBlank()) name else "Direct", headers = streamHeaders))
                }

                // Internal animeindo embed page (e.g. /embed/20405)
                else -> {
                    val reqHeaders = Headers.Builder()
                        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .add("Referer", "$baseUrl/")
                        .add("Accept", "*/*")
                        .build()
                    val doc = runCatching { client.newCall(GET(url, reqHeaders)).awaitSuccess().useAsJsoup() }.getOrNull()

                    val subIframe = doc?.selectFirst("iframe")?.attr("src")?.ifEmpty { doc.selectFirst("iframe")?.attr("data-src") }
                    if (!subIframe.isNullOrBlank() && subIframe != url) {
                        getVideoList(subIframe, name)
                    } else {
                        val videoList = mutableListOf<Video>()
                        val videoSrc = doc?.selectFirst("video source, source, video")?.attr("src")
                        if (!videoSrc.isNullOrBlank()) {
                            if (videoSrc.contains(".m3u8")) {
                                videoList.addAll(playlistUtils.extractFromHls(videoSrc, referer = url, videoNameGen = { if (name.isNotBlank()) "$name - $it" else it }))
                            } else {
                                videoList.add(Video(videoSrc, if (name.isNotBlank()) name else "Video", headers = reqHeaders))
                            }
                        }

                        val htmlContent = doc?.html().orEmpty()
                        val scriptData = doc?.select("script")?.joinToString("\n") { it.data() }.orEmpty()
                        val combinedText = buildString {
                            append(htmlContent)
                            append("\n")
                            append(scriptData)
                            if (JsUnpacker.detect(scriptData)) {
                                append("\n")
                                append(JsUnpacker.unpack(scriptData).joinToString("\n"))
                            }
                        }

                        val sourceMatches = Regex("""(?:file|source|src|url)\s*:\s*["']([^"']+\.(?:mp4|m3u8)[^"']*)["']""", RegexOption.IGNORE_CASE).findAll(combinedText)
                        sourceMatches.forEach { match ->
                            val sUrl = match.groupValues[1]
                            if (sUrl.startsWith("http")) {
                                if (sUrl.contains(".m3u8")) {
                                    videoList.addAll(playlistUtils.extractFromHls(sUrl, referer = url, videoNameGen = { if (name.isNotBlank()) "$name - $it" else it }))
                                } else {
                                    videoList.add(Video(sUrl, if (name.isNotBlank()) name else "Direct", headers = reqHeaders))
                                }
                            }
                        }

                        if (videoList.isNotEmpty()) {
                            videoList.distinctBy { it.videoUrl }
                        } else {
                            emptyList()
                        }
                    }
                }
            }
        }.getOrDefault(emptyList())
    }
}
