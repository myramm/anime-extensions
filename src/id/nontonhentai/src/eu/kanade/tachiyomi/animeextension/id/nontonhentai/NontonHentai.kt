package eu.kanade.tachiyomi.animeextension.id.nontonhentai

import android.util.Base64
import aniyomi.lib.bloggerextractor.BloggerExtractor
import aniyomi.lib.mp4uploadextractor.Mp4uploadExtractor
import aniyomi.lib.pixeldrainextractor.PixelDrainExtractor
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
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Locale

class NontonHentai : ParsedAnimeHttpLegacySource() {
    override val name: String = "NontonHentai"

    override val baseUrl: String = "https://nontonhentai.net"

    override val lang: String = "id"

    override val supportsLatest: Boolean = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")

    // ============================== Popular ===============================
    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/anime/page/$page/?order=popular", headers)

    override fun popularAnimeSelector(): String = "div.listupd article.bsx, div.listupd article, article.bsx"

    override fun popularAnimeFromElement(element: Element): SAnime = SAnime.create().apply {
        val link = element.selectFirst("a") ?: return@apply
        setUrlWithoutDomain(link.attr("href"))
        title = element.selectFirst(".tt h2, .tt, h2, a")?.text()?.trim() ?: link.attr("title").trim()
        thumbnail_url = element.selectFirst("img")?.let { it.attr("src").ifEmpty { it.attr("data-src") } }
    }

    override fun popularAnimeNextPageSelector(): String? = "div.pagination a.next, a.next, nav.pagination a.next"

    override fun popularAnimeParse(response: Response): AnimesPage {
        val document = response.useAsJsoup()
        val anime = document.select(popularAnimeSelector())
            .mapNotNull { runCatching { popularAnimeFromElement(it) }.getOrNull() }
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
            .distinctBy { it.url.trim().removeSuffix("/") }
        val hasNextPage = latestUpdatesNextPageSelector()?.let { document.selectFirst(it) != null } ?: false
        return AnimesPage(anime, hasNextPage)
    }

    // =============================== Search ===============================
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        return if (query.isNotBlank()) {
            if (page == 1) GET("$baseUrl/?s=$query", headers) else GET("$baseUrl/page/$page/?s=$query", headers)
        } else {
            val params = NontonHentaiFilters.getSearchParameters(filters)
            GET("$baseUrl/anime/page/$page/?$params", headers)
        }
    }

    override fun searchAnimeSelector(): String = popularAnimeSelector()

    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun searchAnimeNextPageSelector(): String? = popularAnimeNextPageSelector()

    override fun searchAnimeParse(response: Response): AnimesPage {
        val document = response.useAsJsoup()
        val anime = document.select(searchAnimeSelector())
            .mapNotNull { runCatching { searchAnimeFromElement(it) }.getOrNull() }
            .distinctBy { it.url.trim().removeSuffix("/") }
        val hasNextPage = searchAnimeNextPageSelector()?.let { document.selectFirst(it) != null } ?: false
        return AnimesPage(anime, hasNextPage)
    }

    override fun getFilterList(): AnimeFilterList = NontonHentaiFilters.FILTER_LIST

    // =========================== Anime Details ============================
    override fun animeDetailsParse(document: Document): SAnime = SAnime.create().apply {
        title = document.selectFirst("h1.entry-title, .entry-title")?.text()?.trim().orEmpty()
        thumbnail_url = document.selectFirst("div.thumb img, div.bigcontent img")?.let { it.attr("src").ifEmpty { it.attr("data-src") } }

        val statusText = document.selectFirst("div.info-content span:contains(Status), div.spe span:contains(Status)")?.text().orEmpty()
        status = when {
            statusText.contains("Completed", ignoreCase = true) -> SAnime.COMPLETED
            statusText.contains("Ongoing", ignoreCase = true) -> SAnime.ONGOING
            else -> SAnime.UNKNOWN
        }

        val studio = document.selectFirst("div.info-content span:contains(Studio), div.spe span:contains(Studio)")?.text()?.substringAfter(":")?.trim().orEmpty()
        author = studio

        genre = document.select("div.genxed a, .genre-info a").eachText().joinToString()

        val synopsis = document.selectFirst("div.entry-content[itemprop='description'], div.entry-content, div.desc")?.text()?.trim().orEmpty()
        val type = document.selectFirst("div.info-content span:contains(Tipe), div.spe span:contains(Tipe)")?.text()?.substringAfter(":")?.trim().orEmpty()
        val censor = document.selectFirst("div.info-content span:contains(Censor), div.spe span:contains(Censor)")?.text()?.substringAfter(":")?.trim().orEmpty()
        val totalEp = document.selectFirst("div.info-content span:contains(Episode), div.spe span:contains(Episode)")?.text()?.substringAfter(":")?.trim().orEmpty()
        val duration = document.selectFirst("div.info-content span:contains(Durasi), div.spe span:contains(Durasi)")?.text()?.substringAfter(":")?.trim().orEmpty()

        description = buildString {
            if (synopsis.isNotBlank()) append("$synopsis\n\n")
            if (type.isNotBlank()) append("Tipe: $type\n")
            if (totalEp.isNotBlank()) append("Episode: $totalEp\n")
            if (duration.isNotBlank()) append("Durasi: $duration\n")
            if (censor.isNotBlank()) append("Censor: $censor\n")
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

    private suspend fun getVideosFromEmbed(server: String, link: String): List<Video> {
        if (link.isBlank()) return emptyList()

        val cleanHeaders = Headers.Builder()
            .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build()

        return runCatching {
            when {
                // HentaiCop / LocalStream player
                "hentaicop" in link || "play.php" in link -> {
                    getVideosFromHentaiCop(server, link)
                }

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

    private suspend fun getVideosFromHentaiCop(serverName: String, url: String): List<Video> {
        val reqHeaders = Headers.Builder()
            .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .add("Referer", "$baseUrl/")
            .build()

        val html = client.newCall(GET(url, reqHeaders)).awaitSuccess().body.string()
        val pMatch = Regex("""var\s+p\s*=\s*["']([^"']+)["']""").find(html)?.groupValues?.get(1) ?: return emptyList()
        val d1 = pMatch.reversed()
        val d2 = try {
            String(Base64.decode(d1, Base64.DEFAULT), Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
        val d3 = try {
            URLDecoder.decode(d2, "UTF-8")
        } catch (e: Exception) {
            d2
        }

        val kodeMatch = Regex("""var\s+kodeRHS\s*=\s*["']([^"']+)["']""").find(d3)?.groupValues?.get(1) ?: return emptyList()
        val decodedKodeRaw = try {
            String(Base64.decode(kodeMatch, Base64.DEFAULT), Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
        val decodedKode = try {
            URLDecoder.decode(decodedKodeRaw, "UTF-8")
        } catch (e: Exception) {
            decodedKodeRaw
        }

        val videoList = mutableListOf<Video>()

        val fileRegex = Regex("""["']file["']\s*:\s*["']([^"']+)["']""")
        val labelRegex = Regex("""["']label["']\s*:\s*["']([^"']+)["']""")

        val files = fileRegex.findAll(decodedKode).map { it.groupValues[1].replace("\\/", "/") }.toList()
        val labels = labelRegex.findAll(decodedKode).map { it.groupValues[1] }.toList()

        val streamHeaders = Headers.Builder()
            .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .add("Referer", "https://hentaicop.com/")
            .build()

        for (i in files.indices) {
            val fileUrl = files[i]
            val label = labels.getOrNull(i) ?: serverName
            val qualityName = if (serverName.isNotBlank() && serverName != "Default") "$serverName ($label)" else label
            if (fileUrl.startsWith("http")) {
                videoList.add(Video(fileUrl, qualityName, headers = streamHeaders))
            }
        }

        if (videoList.isEmpty()) {
            val anyUrl = Regex("""https?://[^\s"'<>]+(?:\.m3u8|\.mp4)[^\s"'<>]*""").find(decodedKode)?.value
            if (!anyUrl.isNullOrBlank()) {
                val cleanUrl = anyUrl.replace("\\/", "/")
                videoList.add(Video(cleanUrl, serverName, headers = streamHeaders))
            }
        }

        return videoList
    }
}
