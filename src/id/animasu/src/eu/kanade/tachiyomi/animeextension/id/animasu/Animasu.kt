package eu.kanade.tachiyomi.animeextension.id.animasu

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
import eu.kanade.tachiyomi.multisrc.animestream.AnimeStream
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import keiyoushi.lib.jsunpacker.JsUnpacker
import keiyoushi.utils.parallelCatchingFlatMapBlocking
import keiyoushi.utils.tryParse
import keiyoushi.utils.useAsJsoup
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class Animasu :
    AnimeStream(
        "id",
        "Animasu",
        "https://animasu.love",
    ) {

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")

    override val animeListUrl = "$baseUrl/pencarian"

    override val dateFormatter by lazy {
        SimpleDateFormat("MMMM d, yyyy", Locale(lang))
    }

    // ============================== Popular ===============================
    override fun popularAnimeRequest(page: Int) = GET("$baseUrl/pencarian/?urutan=populer&halaman=$page", headers)

    override fun popularAnimeParse(response: Response): AnimesPage {
        val document = response.useAsJsoup()
        val animes = document.select(popularAnimeSelector())
            .mapNotNull { runCatching { popularAnimeFromElement(it) }.getOrNull() }
            .distinctBy { it.url.trim().removeSuffix("/") }
        val hasNextPage = popularAnimeNextPageSelector()?.let { document.selectFirst(it) != null } ?: false
        return AnimesPage(animes, hasNextPage)
    }

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/pencarian/?urutan=update&halaman=$page", headers)

    override fun latestUpdatesParse(response: Response): AnimesPage {
        val document = response.useAsJsoup()
        val animes = document.select(latestUpdatesSelector())
            .mapNotNull { runCatching { latestUpdatesFromElement(it) }.getOrNull() }
            .distinctBy { it.url.trim().removeSuffix("/") }
        val hasNextPage = latestUpdatesNextPageSelector()?.let { document.selectFirst(it) != null } ?: false
        return AnimesPage(animes, hasNextPage)
    }

    // =============================== Search ===============================
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        if (query.isNotBlank()) {
            return if (page == 1) {
                GET("$baseUrl/?s=$query", headers)
            } else {
                GET("$baseUrl/page/$page/?s=$query", headers)
            }
        }

        val order = filters.filterIsInstance<AnimasuFilters.OrderFilter>().firstOrNull()?.toUriPart() ?: "update"
        val status = filters.filterIsInstance<AnimasuFilters.StatusFilter>().firstOrNull()?.toUriPart() ?: ""
        val type = filters.filterIsInstance<AnimasuFilters.TypeFilter>().firstOrNull()?.toUriPart() ?: ""
        val genre = filters.filterIsInstance<AnimasuFilters.GenreFilter>().firstOrNull()?.toUriPart() ?: ""

        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("pencarian")
            addPathSegment("")
            addQueryParameter("urutan", order)
            if (status.isNotBlank()) addQueryParameter("status", status)
            if (type.isNotBlank()) addQueryParameter("tipe", type)
            if (genre.isNotBlank()) addQueryParameter("genre[]", genre)
            addQueryParameter("halaman", page.toString())
        }.build().toString()

        return GET(url, headers)
    }

    override fun searchAnimeParse(response: Response): AnimesPage {
        val document = response.useAsJsoup()
        val animes = document.select(searchAnimeSelector())
            .mapNotNull { runCatching { searchAnimeFromElement(it) }.getOrNull() }
            .distinctBy { it.url.trim().removeSuffix("/") }
        val hasNextPage = searchAnimeNextPageSelector()?.let { document.selectFirst(it) != null } ?: false
        return AnimesPage(animes, hasNextPage)
    }

    // Target only innermost card container to prevent duplicate parent/child selection
    override fun searchAnimeSelector() = "div.listupd div.bsx, div.bsx"

    override fun searchAnimeFromElement(element: Element): SAnime = SAnime.create().apply {
        val link = element.selectFirst("a") ?: throw Exception("Missing link in anime card")
        val href = link.attr("href").ifEmpty { link.attr("abs:href") }
        if (href.isBlank()) throw Exception("Empty anime URL")
        setUrlWithoutDomain(href)

        val rawTitle = element.selectFirst("div.tt")?.text()?.trim()
            ?: link.attr("title").replace(Regex("(?i)^Nonton Anime\\s*"), "").trim()
            ?: link.text().trim()
        title = rawTitle.ifEmpty { "Anime" }

        thumbnail_url = element.selectFirst("img")?.getImageUrl()
    }

    override fun searchAnimeNextPageSelector() = "div.hpage a.r, div.pagination a.next, a.next.page-numbers"

    // ============================== Filters ===============================
    override val fetchFilters = false
    override fun getFilterList() = AnimasuFilters.FILTER_LIST

    // =========================== Anime Details ============================
    override fun animeDetailsParse(document: Document): SAnime = SAnime.create().apply {
        setUrlWithoutDomain(document.location())

        val rawTitle = document.selectFirst("div.infox h1, h1.entry-title, h1")?.text()?.trim().orEmpty()
        val altTitle = document.selectFirst("div.infox span.alter, .alter")?.text()?.trim()
        title = altTitle?.takeIf(String::isNotBlank)
            ?: rawTitle.replace(Regex("(?i)\\s*Sub\\s*Indo\\s*$"), "").trim().ifEmpty { rawTitle }

        thumbnail_url = document.selectFirst("div.bigcontent > div.thumb > img, div.thumb > img, div.limage > img, .thumb img")?.getImageUrl()

        val spe = document.selectFirst("div.infox div.spe, div.spe, div.info-content, div.right ul.data")

        genre = spe?.select("span:contains(Genre) a, a[href*=/genre/]")?.eachText()?.joinToString()
            ?: document.select("div.genxed a, .genxed a").eachText().joinToString()

        val statusStr = spe?.selectFirst("span:contains(Status)")?.text()
            ?.replace(Regex("(?i)Status\\s*:?"), "")?.trim()
        status = parseStatus(statusStr)

        artist = spe?.selectFirst("span:contains(Studio) a, span:contains(Studio)")?.text()
            ?.replace(Regex("(?i)Studio\\s*:?"), "")?.trim()

        author = spe?.selectFirst("span:contains(Fansub) a, span:contains(Author), span:contains(Pengarang)")?.text()
            ?.replace(Regex("(?i)(Fansub|Author|Pengarang)\\s*:?"), "")?.trim()

        val desc = document.select("div.sinopsis span.desc, div.sinopsis p, div.entry-content[itemprop=description] p, div.desc p, div.sinopsis, div.entry-content")
            .eachText()
            .joinToString("\n\n")
            .ifEmpty {
                document.selectFirst("div.sinopsis, div.desc, div.entry-content")?.text().orEmpty()
            }

        description = buildString {
            if (desc.isNotBlank()) {
                append("$desc\n\n")
            }
            if (!altTitle.isNullOrBlank() && altTitle != rawTitle) {
                append("Judul Alternatif: $altTitle\n")
            }
            spe?.select("span")?.forEach { s ->
                val text = s.text().trim()
                if (text.isNotBlank() && !text.startsWith("Sinopsis", true)) {
                    append("$text\n")
                }
            }
        }.trim()

        initialized = true
    }

    override fun parseStatus(statusString: String?): Int = when (statusString?.trim()?.lowercase()) {
        "completed", "selesai", "tamat" -> SAnime.COMPLETED
        "ongoing", "sedang tayang", "tayang" -> SAnime.ONGOING
        else -> SAnime.UNKNOWN
    }

    // ============================== Episodes ==============================
    override fun episodeListSelector() = "ul#daftarepisode > li, ul.daftarepisode > li, div.eplister ul li, div.listeps ul li, div.bxcl ul li, div.lstepsiode ul li"

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.useAsJsoup()
        val episodeElements = document.select(episodeListSelector())

        return episodeElements.mapNotNull { el ->
            runCatching { episodeFromElement(el) }.getOrNull()
        }.distinctBy { it.url.trim().removeSuffix("/") }
    }

    override fun episodeFromElement(element: Element): SEpisode = SEpisode.create().apply {
        val link = element.selectFirst("span.lchx a, span.epl-num a, div.epl-title a, a")
            ?: throw Exception("No episode link found in element")

        val href = link.attr("href").ifEmpty { link.attr("abs:href") }
        if (href.isBlank()) throw Exception("Empty episode URL")
        setUrlWithoutDomain(href)

        val fullText = link.text().trim()
        val numMatch = Regex("""(?:Episode|Ep|Eps)\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE).find(fullText)
            ?: Regex("""(\d+(?:\.\d+)?)""").find(fullText)

        val epNumStr = numMatch?.groupValues?.getOrNull(1)
        episode_number = epNumStr?.toFloatOrNull() ?: 1F

        name = when {
            epNumStr != null -> "Episode $epNumStr"
            fullText.isNotBlank() -> fullText
            else -> "Episode 1"
        }

        val dateText = element.selectFirst("span.date, span.epl-date, span.zeebr, .date")?.text()?.trim()
        date_upload = dateText?.let { dateFormatter.tryParse(it) } ?: 0L
    }

    // ============================ Video Links =============================
    // ============================ Video Links =============================
    override fun videoListSelector() = "select.mirror option, select#selectserver option, ul.mirror a[data-em], div#pembed iframe, div.player-embed iframe, iframe#p-iframe, .player-embed iframe, div.responsive-embed-stream iframe, div#embed_holder iframe"

    override fun videoListParse(response: Response): List<Video> {
        val document = response.useAsJsoup()
        val defaultIframe = document.selectFirst("div#pembed iframe, div.player-embed iframe, iframe#p-iframe, .player-embed iframe, iframe[src*=/embed], div.responsive-embed-stream iframe, div#embed_holder iframe")
            ?.let { it.attr("src").ifEmpty { it.attr("data-src") } }
            ?.trim()

        val mirrorItems = document.select(videoListSelector())

        val mirrorServerList = mirrorItems.mapNotNull { element ->
            val name = element.text().trim()
            val rawData = when (element.tagName().lowercase()) {
                "option" -> element.attr("value").trim()
                "a" -> element.attr("data-em").ifEmpty { element.attr("href") }.trim()
                "iframe" -> element.attr("src").ifEmpty { element.attr("data-src") }.trim()
                else -> element.attr("href").trim()
            }

            val url = if (rawData.isBlank() && !defaultIframe.isNullOrBlank()) {
                extractIframeUrl(defaultIframe)
            } else if (rawData.isNotBlank()) {
                extractIframeUrl(rawData)
            } else {
                ""
            }

            if (url.isNotBlank() && !name.contains("Pilih", ignoreCase = true)) {
                Pair(url, name.ifEmpty { "Default" })
            } else null
        }.toMutableList()

        if (mirrorServerList.isEmpty() && !defaultIframe.isNullOrBlank()) {
            mirrorServerList.add(Pair(extractIframeUrl(defaultIframe), "Default"))
        }

        // Parse Download section for all quality resolutions (360p, 480p, 720p, 1080p)
        val downloadContainers = document.select("div.soradl, div.soraddl, div.soraurl, div.smokedl, div.download, div.download-eps, div.mctnx, div.bghome, .mctn, div.dlx, div.links_table, div.box-download, div.soraurlx")
        val downloadEntries = downloadContainers.select("div.soraurlx, ul li, li, tr, p").ifEmpty { downloadContainers }
        val downloadServerList = downloadEntries.flatMap { entry ->
            val quality = entry.selectFirst("strong, b, span.fl-l, span, th, td.quality, .quality")?.text()?.trim() ?: "Download"
            entry.select("a[href]").mapNotNull { a ->
                val server = a.text().trim()
                val href = a.attr("href").trim()
                val lowerHref = href.lowercase()
                val isSupported = href.startsWith("http") && (
                    "filedon" in lowerHref || "pixeldrain" in lowerHref || "vidhide" in lowerHref ||
                    "streamwish" in lowerHref || "blogger" in lowerHref || "blogspot" in lowerHref ||
                    "mp4upload" in lowerHref || "yourupload" in lowerHref || "streamtape" in lowerHref ||
                    "dood" in lowerHref || "ok.ru" in lowerHref || "gdrive" in lowerHref ||
                    "drive.google" in lowerHref || "mega.nz" in lowerHref || "mediafire" in lowerHref ||
                    lowerHref.endsWith(".mp4") || lowerHref.endsWith(".m3u8") || lowerHref.endsWith(".mkv")
                )
                if (isSupported) {
                    val label = if (server.isNotBlank() && !quality.contains(server, ignoreCase = true)) "$server ($quality)" else quality
                    Pair(href, label)
                } else null
            }
        }

        val allServers = (mirrorServerList + downloadServerList).distinctBy { "${it.first}|${it.second}" }

        val videos: List<Video> = allServers.parallelCatchingFlatMapBlocking { server ->
            getVideoList(server.first, server.second)
        }
        return videos.distinctBy { it.videoUrl }
    }

    override suspend fun getHosterUrl(element: Element): String {
        val rawData = when (element.tagName().lowercase()) {
            "option" -> element.attr("value").trim()
            "a" -> element.attr("data-em").trim()
            "iframe" -> element.attr("src").ifEmpty { element.attr("data-src") }.trim()
            else -> element.attr("href").trim()
        }
        if (rawData.isBlank()) return ""
        return extractIframeUrl(rawData)
    }

    private fun extractIframeUrl(data: String): String {
        if (data.startsWith("http://") || data.startsWith("https://")) return data
        if (data.startsWith("//")) return "https:$data"

        val decoded = try {
            String(Base64.decode(data, Base64.DEFAULT), Charsets.UTF_8).trim()
        } catch (e: Exception) {
            data
        }

        if (decoded.startsWith("http://") || decoded.startsWith("https://")) return decoded
        if (decoded.startsWith("//")) return "https:$decoded"

        val doc = Jsoup.parse(decoded)
        val iframe = doc.selectFirst("iframe")
        var src = iframe?.attr("src")?.ifEmpty { iframe.attr("data-src") }
            ?.ifEmpty { iframe.attr("data-litespeed-src") }
            ?.ifEmpty { iframe.attr("data-lazy-src") }
            ?: doc.selectFirst("source")?.attr("src")
            ?: doc.selectFirst("meta[itemprop=embedUrl]")?.attr("content")
            ?: doc.selectFirst("a")?.attr("href")
            ?: ""

        if (src.isBlank()) {
            val match = Regex("""(?:src|data-src|file|link|source)\s*[:=]\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(decoded)
                ?: Regex("""https?://[^\s"'<>]+""").find(decoded)
            src = match?.groupValues?.getOrNull(1) ?: match?.value ?: ""
        }

        return when {
            src.startsWith("//") -> "https:$src"
            src.startsWith("/") -> "$baseUrl$src"
            else -> src
        }
    }

    private val mp4uploadExtractor by lazy { Mp4uploadExtractor(client) }
    private val gdrivePlayerExtractor by lazy { GdrivePlayerExtractor(client) }
    private val streamTapeExtractor by lazy { StreamTapeExtractor(client) }
    private val yourUploadExtractor by lazy { YourUploadExtractor(client) }
    private val okruExtractor by lazy { OkruExtractor(client) }
    private val bloggerExtractor by lazy { BloggerExtractor(client) }
    private val doodExtractor by lazy { DoodExtractor(client) }
    private val pixelDrainExtractor by lazy { PixelDrainExtractor() }
    private val playlistUtils by lazy { PlaylistUtils(client, headers) }

    override suspend fun getVideoList(url: String, name: String): List<Video> {
        if (url.isBlank()) return emptyList()
        val lowerName = name.lowercase()
        val lowerUrl = url.lowercase()

        // Clean headers specifically for video playback without HTML accept headers and without host-mismatched referers
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

                // Filedon / Uservideo / Userdrive / Samevideo (Inertia R2 apps)
                "filedon" in lowerUrl || "uservideo" in lowerUrl || "userdrive" in lowerUrl || "samevideo" in lowerUrl || "samehadaku" in lowerUrl -> {
                    val embedUrl = if ("/view/" in url) url.replace("/view/", "/embed/") else url
                    val r2Headers = Headers.Builder()
                        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .add("Referer", embedUrl)
                        .add("Accept", "*/*")
                        .build()
                    val doc = client.newCall(GET(embedUrl, r2Headers)).awaitSuccess().useAsJsoup()
                    val dataPage = doc.selectFirst("div#app")?.attr("data-page")
                    if (!dataPage.isNullOrBlank()) {
                        val json = JSONObject(dataPage)
                        val props = json.optJSONObject("props")
                        val qualities = props?.optJSONArray("qualities")
                        if (qualities != null && qualities.length() > 0) {
                            (0 until qualities.length()).mapNotNull { i ->
                                val qObj = qualities.optJSONObject(i) ?: return@mapNotNull null
                                val qUrl = qObj.optString("url")
                                val qLabel = qObj.optString("label", "Video")
                                if (qUrl.isNotBlank() && qUrl.startsWith("http")) {
                                    Video(qUrl, "${if (name.isNotBlank()) "$name - " else ""}Filedon ($qLabel)", headers = r2Headers)
                                } else null
                            }
                        } else {
                            val videoUrl = props?.optString("url")
                            if (!videoUrl.isNullOrBlank() && videoUrl.startsWith("http")) {
                                listOf(Video(videoUrl, if (name.isNotBlank()) name else "Filedon", headers = r2Headers))
                            } else emptyList()
                        }
                    } else {
                        val src = doc.selectFirst("video source, video")?.attr("src")
                        if (!src.isNullOrBlank()) {
                            listOf(Video(src, if (name.isNotBlank()) name else "Filedon", headers = r2Headers))
                        } else emptyList()
                    }
                }

                // VidHide
                "vidhide" in lowerName || "vidhide" in lowerUrl || "streamhide" in lowerUrl || "odvidhide" in lowerUrl || "vidlion" in lowerUrl -> {
                    VidHideExtractor(client, cleanHeaders).videosFromUrl(url)
                }

                // StreamWish / FileLions / WishFast / Medixiru / Niramirus
                "streamwish" in lowerName || "streamwish" in lowerUrl || "filelions" in lowerUrl || "wishembed" in lowerUrl || "wishfast" in lowerUrl || "medixiru" in lowerUrl || "niramirus" in lowerUrl || "strwish" in lowerUrl || "dwish" in lowerUrl -> {
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
                "streamtape" in lowerName || "streamtape" in lowerUrl || "streamta.pe" in lowerUrl || "tapewith" in lowerUrl || "adblocksurvey" in lowerUrl -> {
                    streamTapeExtractor.videoFromUrl(url)?.let(::listOf).orEmpty()
                }

                // Ok.ru
                "ok.ru" in lowerUrl || "okru" in lowerName || "odnoklassniki" in lowerUrl -> {
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
                "dood" in lowerName || "dood" in lowerUrl || "ds2play" in lowerUrl || "doodstream" in lowerUrl -> {
                    doodExtractor.videosFromUrl(url)
                }

                // Google Drive Player
                "gdrive" in lowerName || "gdrive" in lowerUrl -> {
                    val gdriveUrl = when {
                        baseUrl in url -> "https:" + (url.toHttpUrlOrNull()?.queryParameter("data") ?: url)
                        else -> url
                    }
                    gdrivePlayerExtractor.videosFromUrl(gdriveUrl, "Gdrive", cleanHeaders)
                }

                // HLS / m3u8 stream
                url.contains(".m3u8") -> {
                    playlistUtils.extractFromHls(url, referer = "$baseUrl/", videoNameGen = { if (name.isNotBlank()) "$name - $it" else it })
                }

                // Direct video link
                url.endsWith(".mp4") || url.contains(".mp4?") -> {
                    val streamHeaders = Headers.Builder()
                        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .add("Accept", "*/*")
                        .build()
                    listOf(Video(url, if (name.isNotBlank()) name else "Direct", headers = streamHeaders))
                }

                // Internal wrapper or generic iframe page
                else -> {
                    val reqHeaders = Headers.Builder()
                        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .add("Referer", url)
                        .add("Accept", "*/*")
                        .build()
                    val doc = runCatching { client.newCall(GET(url, reqHeaders)).awaitSuccess().useAsJsoup() }.getOrNull()
                    val subIframe = doc?.selectFirst("iframe")?.attr("src")?.ifEmpty { doc.selectFirst("iframe")?.attr("data-src") }
                    if (!subIframe.isNullOrBlank() && subIframe != url) {
                        getVideoList(extractIframeUrl(subIframe), name)
                    } else {
                        val videoList = mutableListOf<Video>()
                        val videoSrc = doc?.selectFirst("video source, video")?.attr("src")
                        if (!videoSrc.isNullOrBlank()) {
                            if (videoSrc.contains(".m3u8")) {
                                videoList.addAll(playlistUtils.extractFromHls(videoSrc, referer = url, videoNameGen = { if (name.isNotBlank()) "$name - $it" else it }))
                            } else {
                                videoList.add(Video(videoSrc, if (name.isNotBlank()) name else "Video", headers = reqHeaders))
                            }
                        }

                        // Deep scan scripts and HTML for PlayerJS / JWPlayer / HLS / unpacked JS
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

                        // Check PlayerJS multi quality [480p]https://...,[720p]https://...
                        val playerJsMatches = Regex("""\[(\d+p?)\](https?://[^\s,\[\]\"\'<>]+)""").findAll(combinedText)
                        playerJsMatches.forEach { match ->
                            val q = match.groupValues[1]
                            val vUrl = match.groupValues[2]
                            if (vUrl.contains(".m3u8")) {
                                videoList.addAll(playlistUtils.extractFromHls(vUrl, referer = url, videoNameGen = { "$name ($q) - $it" }))
                            } else {
                                videoList.add(Video(vUrl, "$name ($q)", headers = reqHeaders))
                            }
                        }

                        // Check JS sources/file
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
                            Log.i("Animasu", "Unrecognized server at getVideoList => Name -> $name || URL => $url")
                            emptyList()
                        }
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    override fun Element.getImageUrl(): String? {
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

