package eu.kanade.tachiyomi.animeextension.id.nontonanimeid

import android.util.Base64
import aniyomi.lib.bloggerextractor.BloggerExtractor
import aniyomi.lib.gdriveplayerextractor.GdrivePlayerExtractor
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
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class NontonAnimeID : ParsedAnimeHttpLegacySource() {
    override val name: String = "NontonAnimeID"

    override val baseUrl: String = "https://s13.nontonanimeid.boats"

    override val lang: String = "id"

    override val supportsLatest: Boolean = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")

    // ============================== Popular ===============================
    override fun popularAnimeRequest(page: Int): Request =
        if (page == 1) GET("$baseUrl/anime-populer/", headers) else GET("$baseUrl/anime-populer/page/$page/", headers)

    override fun popularAnimeSelector(): String = "article.animpost, div.animpost, div.bsx, article.bsx, .result-item, div.item, div.listupd article"

    override fun popularAnimeFromElement(element: Element): SAnime = SAnime.create().apply {
        val link = element.selectFirst("a") ?: return@apply
        setUrlWithoutDomain(link.attr("href"))
        title = element.selectFirst(".title, h2, h3, .tt, a")?.text()?.trim() ?: link.attr("title").trim()
        thumbnail_url = element.selectFirst("img")?.let { it.attr("src").ifEmpty { it.attr("data-src") }.ifEmpty { it.attr("data-lazy-src") } }
    }

    override fun popularAnimeNextPageSelector(): String? = "div.pagination a.next, a.next, nav.pagination a.next, .nav-links a.next"

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
        if (page == 1) GET("$baseUrl/anime-terbaru/", headers) else GET("$baseUrl/anime-terbaru/page/$page/", headers)

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
            val params = NontonAnimeIDFilters.getSearchParameters(filters)
            if (page == 1) GET("$baseUrl/advanced-search/?$params", headers) else GET("$baseUrl/advanced-search/page/$page/?$params", headers)
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

    override fun getFilterList(): AnimeFilterList = NontonAnimeIDFilters.FILTER_LIST

    // =========================== Anime Details ============================
    override fun animeDetailsParse(document: Document): SAnime = SAnime.create().apply {
        title = document.selectFirst("h1.entry-title, .entry-title, h1")?.text()?.trim().orEmpty()
        thumbnail_url = document.selectFirst("div.thumb img, div.poster img, .entry-content img")
            ?.let { it.attr("src").ifEmpty { it.attr("data-src") } }

        val statusText = document.selectFirst("div.info-content span:contains(Status), div.spe span:contains(Status), .list-info li:contains(Status)")?.text().orEmpty()
        status = when {
            statusText.contains("Completed", ignoreCase = true) -> SAnime.COMPLETED
            statusText.contains("Ongoing", ignoreCase = true) -> SAnime.ONGOING
            else -> SAnime.UNKNOWN
        }

        val studio = document.selectFirst("div.info-content span:contains(Studio), div.spe span:contains(Studio), .list-info li:contains(Studio)")?.text()?.substringAfter(":")?.trim().orEmpty()
        author = studio

        genre = document.select("div.genxed a, .genre-info a, .genres a, a[rel='tag'], .genre a").eachText().joinToString()

        val synopsis = document.selectFirst("div.entry-content[itemprop='description'], div.entry-content, div.desc, .synopsis, .entry-content p")?.text()?.trim().orEmpty()
        val type = document.selectFirst("div.info-content span:contains(Tipe), div.spe span:contains(Tipe), .list-info li:contains(Tipe)")?.text()?.substringAfter(":")?.trim().orEmpty()
        val totalEp = document.selectFirst("div.info-content span:contains(Episode), div.spe span:contains(Episode), .list-info li:contains(Episode)")?.text()?.substringAfter(":")?.trim().orEmpty()
        val duration = document.selectFirst("div.info-content span:contains(Durasi), div.spe span:contains(Durasi), .list-info li:contains(Durasi)")?.text()?.substringAfter(":")?.trim().orEmpty()

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
    override fun episodeListSelector(): String = "div.episodelist ul li, ul.clstyle li, div.mepisodes ul li, #single-episode ul li, ul.episodelist li, div.eplister ul li"

    private val episodePattern = Regex("""(?i)(?:Episode|Ep|Eps)\s*(\d+(?:\.\d+)?)""")
    private val dateFormatter = SimpleDateFormat("MMMM d, yyyy", Locale("id", "ID"))
    private val dateFormatterEn = SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH)

    override fun episodeFromElement(element: Element): SEpisode = SEpisode.create().apply {
        val link = element.selectFirst("a") ?: throw Exception("Missing episode link")
        setUrlWithoutDomain(link.attr("href"))

        val title = element.selectFirst(".epl-title, .title, a")?.text()?.trim() ?: link.text().trim()
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
        val optionElements = doc.select("select.mirror option, div.mirror option, .mirror option, select#select-server option, ul#server li")

        val mirrorOptions = optionElements.mapNotNull { opt ->
            val name = opt.text().trim()
            val b64 = opt.attr("value").ifEmpty { opt.attr("data-video") }.ifEmpty { opt.attr("data-url") }.trim()
            if (b64.isNotBlank() && !name.contains("Pilih", ignoreCase = true)) {
                val decoded = b64.b64Decode()
                val iframeSrc = Regex("""src\s*=\s*["']([^"']+)["']""").find(decoded)?.groupValues?.get(1)
                    ?: if (decoded.startsWith("http")) decoded else null
                if (!iframeSrc.isNullOrBlank()) Pair(name, iframeSrc) else null
            } else null
        }

        val iframeElements = doc.select("iframe, div.player-embed iframe, div#embed_holder iframe, div.responsive-embed-stream iframe, .video-content iframe, div#player_embed iframe")
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
    private val gdrivePlayerExtractor by lazy { GdrivePlayerExtractor(client) }

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

                "drive.google" in link -> {
                    gdrivePlayerExtractor.videosFromUrl(link, server, headers = cleanHeaders)
                }

                link.endsWith(".mp4") || link.endsWith(".m3u8") || link.contains(".mp4?") || link.contains(".m3u8?") -> {
                    listOf(Video(link, server, headers = cleanHeaders))
                }

                else -> emptyList()
            }
        }.getOrDefault(emptyList())
    }
}
