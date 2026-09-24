package eu.kanade.tachiyomi.animeextension.id.otakudesu

import android.util.Base64
import aniyomi.lib.bloggerextractor.BloggerExtractor
import aniyomi.lib.mp4uploadextractor.Mp4uploadExtractor
import aniyomi.lib.pixeldrainextractor.PixelDrainExtractor
import aniyomi.lib.streamwishextractor.StreamWishExtractor
import aniyomi.lib.vidhideextractor.VidHideExtractor
import aniyomi.lib.youruploadextractor.YourUploadExtractor
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import keiyoushi.utils.ParsedAnimeHttpLegacySource
import keiyoushi.utils.bodyString
import keiyoushi.utils.parallelCatchingFlatMapBlocking
import keiyoushi.utils.parallelMapNotNullBlocking
import keiyoushi.utils.tryParse
import keiyoushi.utils.useAsJsoup
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class OtakuDesu : ParsedAnimeHttpLegacySource() {
    override val name: String = "OtakuDesu"

    override val baseUrl: String = "https://otakudesu.blog"

    override val lang: String = "id"

    override val supportsLatest: Boolean = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")

    // ============================== Popular ===============================
    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/complete-anime/page/$page", headers)

    override fun popularAnimeSelector(): String = "div.venz ul li div.detpost, div.detpost"

    override fun popularAnimeFromElement(element: Element): SAnime = latestUpdatesFromElement(element)

    override fun popularAnimeNextPageSelector(): String? = "div.pagination a.next"

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/ongoing-anime/page/$page", headers)

    override fun latestUpdatesSelector(): String = "div.venz ul li div.detpost, div.detpost"

    override fun latestUpdatesFromElement(element: Element): SAnime = SAnime.create().apply {
        val link = element.selectFirst("div.thumb a, a") ?: return@apply
        setUrlWithoutDomain(link.attr("href"))
        title = element.selectFirst("div.thumb a div.thumbz h2, h2")?.text().orEmpty()
        thumbnail_url = element.selectFirst("div.thumb a div.thumbz img, img")?.attr("src")
    }

    override fun latestUpdatesNextPageSelector(): String? = "div.pagination a.next"

    // =========================== Anime Details ============================
    override fun animeDetailsParse(document: Document): SAnime = SAnime.create().apply {
        val detail = document.selectFirst("div.infozingle, div.infozin") ?: return@apply
        title = detail.selectFirst("p:contains(Judul) span")?.text()?.substringAfter(" ").orEmpty()
        status = parseStatus(detail.selectFirst("p:contains(Status) span")?.text()?.substringAfter(" ").orEmpty())
        genre = detail.select("p:contains(Genre) span a, p:contains(Genre) a").eachText().joinToString()
        val ep = detail.selectFirst("p:contains(Total) span")?.text()?.substringAfter(" ").orEmpty()
        val score = detail.selectFirst("p:contains(Skor) span")?.text()?.substringAfter(" ").orEmpty()
        val studio = detail.selectFirst("p:contains(Studio) span")?.text()?.substringAfter(" ").orEmpty()
        val produser = detail.selectFirst("p:contains(Produser) span")?.text()?.substringAfter(" ").orEmpty()
        val sinopsis = document.select("div.sinopc p").eachText().joinToString("\n\n").ifBlank {
            document.selectFirst("div.sinopc")?.text().orEmpty()
        }

        description = buildString {
            if (sinopsis.isNotBlank()) append("$sinopsis\n\n")
            if (score.isNotBlank()) append("Skor: $score\n")
            if (ep.isNotBlank()) append("Total Episode: $ep\n")
            if (studio.isNotBlank()) append("Studio: $studio\n")
            if (produser.isNotBlank()) append("Produser: $produser\n")
        }.trim()

        thumbnail_url = document.selectFirst("div.fotoanime img")?.attr("src")
        initialized = true
    }

    private fun parseStatus(status: String): Int = when (status.trim().lowercase()) {
        "completed" -> SAnime.COMPLETED
        "ongoing" -> SAnime.ONGOING
        else -> SAnime.UNKNOWN
    }

    // ============================== Episodes ==============================
    override fun episodeListSelector(): String = "div.episodelist ul li"

    private val episodePattern = Regex("""(?i)(?:Episode|Ep|Eps)\s*(\d+(?:\.\d+)?)""")
    private val dateFormatter = SimpleDateFormat("dd MMMM, yyyy", Locale("id", "ID"))

    override fun episodeFromElement(element: Element): SEpisode = SEpisode.create().apply {
        val link = element.selectFirst("a") ?: throw Exception("Missing episode link")
        setUrlWithoutDomain(link.attr("href"))

        val linkText = link.text().trim()
        name = linkText

        val numMatch = episodePattern.find(linkText)
            ?: Regex("""(\d+(?:\.\d+)?)""").find(linkText)
        episode_number = numMatch?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: 1F

        val dateStr = element.selectFirst("span.zeebr")?.text()?.trim()
        date_upload = dateStr?.let { dateFormatter.tryParse(it) } ?: 0L
    }

    // =============================== Search ===============================
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val filter = filters.filterIsInstance<GenreFilter>().firstOrNull()
        return when {
            query.isNotBlank() -> GET("$baseUrl/?s=$query&post_type=anime", headers)
            filter != null && filter.toUriPart().isNotBlank() -> GET("$baseUrl/genres/${filter.toUriPart()}/page/$page", headers)
            else -> latestUpdatesRequest(page)
        }
    }

    override fun searchAnimeParse(response: Response): AnimesPage {
        val document = response.useAsJsoup()
        val genreSelector = "div.col-anime-con"

        val ui = when {
            document.selectFirst(genreSelector) == null -> "search"
            document.selectFirst("ul.chivsrc li") == null -> "genres"
            else -> "unknown"
        }

        val animes = when (ui) {
            "genres" -> document.select(genreSelector).map { searchAnimeFromElement(it) }
            "search" -> document.select("ul.chivsrc li").map { searchAnimeFromElement(it) }
            else -> document.select(latestUpdatesSelector()).map(::latestUpdatesFromElement)
        }

        val hasNextPage = searchAnimeNextPageSelector()?.let { document.selectFirst(it) != null } ?: false

        return AnimesPage(animes, hasNextPage)
    }

    override fun searchAnimeSelector(): String = "ul.chivsrc li, div.col-anime-con"

    override fun searchAnimeFromElement(element: Element): SAnime = SAnime.create().apply {
        val link = element.selectFirst("h2 a, div.col-anime-title a, a")
            ?: throw Exception("Missing search link")
        setUrlWithoutDomain(link.attr("href"))
        title = link.text().trim()

        val img = element.selectFirst("img, div.col-anime-cover img")
        thumbnail_url = img?.attr("src")
    }

    override fun searchAnimeNextPageSelector(): String? = "div.pagination a.next"

    // ============================ Video Links =============================
    override fun videoListSelector(): String = throw UnsupportedOperationException()
    override fun videoFromElement(element: Element): Video = throw UnsupportedOperationException()

    override fun videoListParse(response: Response): List<Video> {
        val doc = response.useAsJsoup()
        val script = doc.selectFirst("script:containsData(action:)")?.data().orEmpty()

        val actions = Regex("""action:\s*["']([a-f0-9]+)["']""").findAll(script).map { it.groupValues[1] }.toList()
        val ajaxVideos = if (actions.isNotEmpty()) {
            val streamAction = actions[0]
            val nonceAction = if (actions.size >= 2) actions[1] else actions[0]
            val nonce = getNonce(nonceAction)
            if (nonce.isNotBlank()) {
                doc.select("div.mirrorstream ul li > a, ul.m360p a, ul.m480p a, ul.m720p a, ul.m1080p a")
                    .parallelMapNotNullBlocking {
                        runCatching { getEmbedLinks(it, streamAction, nonce) }.getOrNull()
                    }
                    .parallelCatchingFlatMapBlocking { server ->
                        getVideosFromEmbed(server.first, server.second)
                    }
            } else emptyList()
        } else emptyList()

        val iframeElements = doc.select("div.responsive-embed-stream iframe, div.embed_holder iframe, div#embed_holder iframe, iframe#p-iframe, .player-embed iframe")
        val iframeVideos = iframeElements.mapNotNull {
            val src = it.attr("src").ifEmpty { it.attr("data-src") }
            if (src.isNotBlank()) Pair("Default", src) else null
        }.parallelCatchingFlatMapBlocking { server ->
            getVideosFromEmbed(server.first, server.second)
        }

        val downloadElements = doc.select("div.download ul li, div.cukder ul li, div.download-eps ul li")
        val downloadVideos = downloadElements.flatMap { li ->
            val quality = li.selectFirst("strong, b, span.fl-l")?.text()?.trim() ?: "Download"
            li.select("a[href]").mapNotNull { a ->
                val server = a.text().trim()
                val href = a.attr("href").trim()
                if (href.startsWith("http")) {
                    val name = if (server.isNotBlank()) "$server ($quality)" else quality
                    Pair(name, href)
                } else null
            }
        }.parallelCatchingFlatMapBlocking { server ->
            val resolvedUrl = resolveDesuLink(server.second)
            getVideosFromEmbed(server.first, resolvedUrl)
        }

        val allVideos = ajaxVideos + iframeVideos + downloadVideos
        return allVideos.distinctBy { it.videoUrl }
    }

    private fun resolveDesuLink(link: String): String {
        if (!link.contains("link.desustream.com")) return link
        return runCatching {
            val noRedirectClient = client.newBuilder().followRedirects(false).build()
            val req = GET(link, headers)
            val resp = noRedirectClient.newCall(req).execute()
            val loc = resp.header("Location")
            resp.close()
            if (loc.isNullOrBlank()) return link
            if ("desudrive.com/fl/?id=" in loc) {
                val id = loc.substringAfter("id=")
                "https://odvidhide.com/embed/$id"
            } else {
                loc
            }
        }.getOrDefault(link)
    }

    private fun String.b64Decode(): String = try {
        String(Base64.decode(this, Base64.DEFAULT), Charsets.UTF_8)
    } catch (e: Exception) {
        this
    }

    private suspend fun getEmbedLinks(element: Element, action: String, nonce: String): Pair<String, String> {
        val contentAttr = element.attr("data-content")
        if (contentAttr.isBlank()) return Pair("Default", "")
        val decodedData = contentAttr.b64Decode()

        val id = decodedData.substringAfter("\"id\":").substringBefore(",").substringBefore("}").trim()
        val mirror = decodedData.substringAfter("\"i\":").substringBefore(",").substringBefore("}").trim()
        val quality = decodedData.substringAfter("\"q\":\"").substringBefore("\"").ifEmpty { "Default" }
        val serverName = element.text().trim().ifEmpty { quality }

        val form = FormBody.Builder().apply {
            add("id", id)
            add("i", mirror)
            add("q", quality)
            add("nonce", nonce)
            add("action", action)
        }.build()

        val resp = client.newCall(POST("$baseUrl/wp-admin/admin-ajax.php", body = form, headers = headers))
            .awaitSuccess()
            .bodyString()

        val b64Html = resp.substringAfter("\"data\":\"").substringBefore("\"")
        if (b64Html.isBlank()) return Pair(serverName, "")

        val doc = Jsoup.parse(b64Html.b64Decode())
        val url = doc.selectFirst("iframe")?.attr("src").orEmpty()

        return Pair("$serverName ($quality)", url)
    }

    private val bloggerExtractor by lazy { BloggerExtractor(client) }
    private val yourUploadExtractor by lazy { YourUploadExtractor(client) }
    private val mp4uploadExtractor by lazy { Mp4uploadExtractor(client) }
    private val pixelDrainExtractor by lazy { PixelDrainExtractor() }

    private suspend fun getVideosFromEmbed(server: String, link: String): List<Video> {
        if (link.isBlank()) return emptyList()
        val videoHeaders = headers.newBuilder()
            .set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .set("Referer", link)
            .build()

        val cleanHeaders = Headers.Builder()
            .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build()

        return runCatching {
            when {
                // Filedon, Uservideo, Samevideo (Inertia R2 extraction)
                "filedon" in link || "uservideo" in link || "userdrive" in link || "samevideo" in link -> {
                    val r2Headers = Headers.Builder()
                        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .add("Referer", link)
                        .add("Accept", "*/*")
                        .build()
                    val doc = client.newCall(GET(link, r2Headers)).awaitSuccess().useAsJsoup()
                    val dataPage = doc.selectFirst("div#app")?.attr("data-page")
                    if (!dataPage.isNullOrBlank()) {
                        val json = JSONObject(dataPage)
                        val props = json.optJSONObject("props")
                        val directUrl = props?.optString("url")
                        if (!directUrl.isNullOrBlank()) {
                            listOf(Video(directUrl, server, headers = r2Headers))
                        } else {
                            val fileObj = props?.optJSONObject("file") ?: props?.optJSONObject("files")
                            val storage = fileObj?.optJSONObject("storage")
                            val config = storage?.optJSONObject("config")
                            val s3Url = config?.optString("s3_url")
                            val path = fileObj?.optString("path")
                            if (!s3Url.isNullOrBlank() && !path.isNullOrBlank()) {
                                val fullUrl = "$s3Url/$path"
                                listOf(Video(fullUrl, server, headers = r2Headers))
                            } else {
                                val src = doc.selectFirst("video source, video")?.attr("src")
                                if (!src.isNullOrBlank()) {
                                    listOf(Video(src, server, headers = r2Headers))
                                } else emptyList()
                            }
                        }
                    } else {
                        val src = doc.selectFirst("video source, video")?.attr("src")
                        if (!src.isNullOrBlank()) {
                            listOf(Video(src, server, headers = r2Headers))
                        } else emptyList()
                    }
                }

                // Blogger video
                "blogger" in link || "bp.blogspot" in link || "video.googleusercontent" in link -> {
                    val bloggerHeaders = Headers.Builder()
                        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .add("Referer", "https://www.blogger.com/")
                        .add("Accept", "*/*")
                        .build()
                    bloggerExtractor.videosFromUrl(link, bloggerHeaders, server)
                }

                // VidHide
                "vidhide" in link || "odvidhide" in link || "streamhide" in link -> {
                    VidHideExtractor(client, cleanHeaders).videosFromUrl(link)
                }

                // YourUpload
                "yourupload" in link -> {
                    val id = link.substringAfter("id=").substringBefore("&")
                    val url = if ("embed" in link) link else "https://yourupload.com/embed/$id"
                    val youruploadHeaders = Headers.Builder()
                        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .add("Referer", "https://www.yourupload.com/")
                        .add("Accept", "*/*")
                        .build()
                    yourUploadExtractor.videoFromUrl(url, youruploadHeaders, server)
                }

                // Mp4upload
                "mp4upload" in link -> {
                    val mp4Headers = Headers.Builder()
                        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .add("Referer", "https://www.mp4upload.com/")
                        .add("Accept", "*/*")
                        .build()
                    mp4uploadExtractor.videosFromUrl(link, mp4Headers)
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

                // DesuStream / Odcdn / Odstream / OtakuWatch
                "desustream" in link || "odcdn" in link || "odstream" in link || "otakuwatch" in link -> {
                    runCatching {
                        val doc = client.newCall(GET(link, videoHeaders)).awaitSuccess().useAsJsoup()
                        val videoSrc = doc.selectFirst("video source, video")?.attr("src").orEmpty()
                        if (videoSrc.isNotBlank()) {
                            val fixedUrl = if (videoSrc.startsWith("//")) "https:$videoSrc" else videoSrc
                            listOf(Video(fixedUrl, server, headers = videoHeaders))
                        } else {
                            val script = doc.selectFirst("script:containsData(sources), script:containsData(file)")?.data().orEmpty()
                            val videoUrl = script.substringAfter("file':'", "")
                                .ifEmpty { script.substringAfter("file:\"", "") }
                                .substringBefore("'")
                                .substringBefore("\"")
                            if (videoUrl.isNotBlank() && (videoUrl.startsWith("http") || videoUrl.startsWith("//"))) {
                                val fixedUrl = if (videoUrl.startsWith("//")) "https:$videoUrl" else videoUrl
                                listOf(Video(fixedUrl, server, headers = videoHeaders))
                            } else emptyList()
                        }
                    }.getOrDefault(emptyList())
                }

                link.endsWith(".mp4") || link.endsWith(".m3u8") || link.contains(".mp4?") || link.contains(".m3u8?") -> {
                    val streamHeaders = Headers.Builder()
                        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .add("Accept", "*/*")
                        .build()
                    listOf(Video(link, server, headers = streamHeaders))
                }

                else -> emptyList()
            }
        }.getOrDefault(emptyList())
    }

    private fun getNonce(action: String): String {
        return runCatching {
            val form = FormBody.Builder().add("action", action).build()
            client.newCall(POST("$baseUrl/wp-admin/admin-ajax.php", body = form, headers = headers))
                .execute()
                .bodyString()
                .substringAfter(":\"")
                .substringBefore('"')
        }.getOrDefault("")
    }

    // ============================== Filters ===============================
    override fun getFilterList(): AnimeFilterList = AnimeFilterList(
        AnimeFilter.Header("Text search ignores filters"),
        GenreFilter(),
    )

    private class GenreFilter :
        UriPartFilter(
            "Genres",
            arrayOf(
                Pair("<select>", ""),
                Pair("Action", "action"),
                Pair("Adventure", "adventure"),
                Pair("Comedy", "comedy"),
                Pair("Demons", "demons"),
                Pair("Drama", "drama"),
                Pair("Ecchi", "ecchi"),
                Pair("Fantasy", "fantasy"),
                Pair("Game", "game"),
                Pair("Harem", "harem"),
                Pair("Historical", "historical"),
                Pair("Horror", "horror"),
                Pair("Josei", "josei"),
                Pair("Magic", "magic"),
                Pair("Martial Arts", "martial-arts"),
                Pair("Mecha", "mecha"),
                Pair("Military", "military"),
                Pair("Music", "music"),
                Pair("Mystery", "mystery"),
                Pair("Psychological", "psychological"),
                Pair("Parody", "parody"),
                Pair("Romance", "romance"),
                Pair("Samurai", "samurai"),
                Pair("School", "school"),
                Pair("Sci-Fi", "sci-fi"),
                Pair("Seinen", "seinen"),
                Pair("Shoujo", "shoujo"),
                Pair("Shoujo Ai", "shoujo-ai"),
                Pair("Shounen", "shounen"),
                Pair("Slice of Life", "slice-of-life"),
                Pair("Sports", "sports"),
                Pair("Space", "space"),
                Pair("Super Power", "super-power"),
                Pair("Supernatural", "supernatural"),
                Pair("Thriller", "thriller"),
                Pair("Vampire", "vampire"),
            ),
        )

    private open class UriPartFilter(
        displayName: String,
        private val vals: Array<Pair<String, String>>,
    ) : AnimeFilter.Select<String>(
        displayName,
        vals.map { it.first }.toTypedArray(),
    ) {
        fun toUriPart() = vals[state].second
    }
}
