package eu.kanade.tachiyomi.animeextension.id.animasu

import android.util.Base64
import android.util.Log
import aniyomi.lib.bloggerextractor.BloggerExtractor
import aniyomi.lib.doodextractor.DoodExtractor
import aniyomi.lib.gdriveplayerextractor.GdrivePlayerExtractor
import aniyomi.lib.mp4uploadextractor.Mp4uploadExtractor
import aniyomi.lib.okruextractor.OkruExtractor
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
import keiyoushi.utils.tryParse
import keiyoushi.utils.useAsJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
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
    override fun videoListSelector() = "select.mirror option[value], select#selectserver option[value], ul.mirror a[data-em], div#pembed iframe, div.player-embed iframe"

    override fun videoListParse(response: Response): List<Video> {
        val document = response.useAsJsoup()
        val videos = mutableListOf<Video>()

        // 1. Check select.mirror option[value]
        document.select("select.mirror option[value], select#selectserver option[value]").forEach { option ->
            val value = option.attr("value").trim()
            if (value.isNotBlank()) {
                val serverName = option.text().trim()
                val iframeUrl = extractIframeUrl(value)
                if (iframeUrl.isNotBlank()) {
                    runCatching {
                        videos.addAll(getVideoListSync(iframeUrl, serverName))
                    }
                }
            }
        }

        // 2. Check ul.mirror a[data-em]
        document.select("ul.mirror a[data-em]").forEach { a ->
            val dataEm = a.attr("data-em").trim()
            if (dataEm.isNotBlank()) {
                val serverName = a.text().trim().ifEmpty { "Server" }
                val iframeUrl = extractIframeUrl(dataEm)
                if (iframeUrl.isNotBlank()) {
                    runCatching {
                        videos.addAll(getVideoListSync(iframeUrl, serverName))
                    }
                }
            }
        }

        // 3. Check direct iframe in #pembed or .player-embed if no videos found
        if (videos.isEmpty()) {
            document.select("div#pembed iframe, div.player-embed iframe, div.responsive-embed-stream iframe, div#embed_holder iframe").forEach { iframe ->
                val src = iframe.attr("src").trim()
                if (src.isNotBlank()) {
                    val fixedSrc = when {
                        src.startsWith("//") -> "https:$src"
                        src.startsWith("/") -> "$baseUrl$src"
                        else -> src
                    }
                    runCatching {
                        videos.addAll(getVideoListSync(fixedSrc, "Default Server"))
                    }
                }
            }
        }

        return videos.distinctBy { it.url }
    }

    private fun extractIframeUrl(data: String): String {
        val decoded = if (data.startsWith("http://") || data.startsWith("https://") || data.startsWith("//")) {
            data
        } else {
            try {
                String(Base64.decode(data, Base64.DEFAULT))
            } catch (e: Exception) {
                data
            }
        }

        if (decoded.startsWith("http://") || decoded.startsWith("https://")) {
            return decoded
        }
        if (decoded.startsWith("//")) {
            return "https:$decoded"
        }

        val doc = Jsoup.parse(decoded)
        val src = doc.selectFirst("iframe")?.attr("src")
            ?: doc.selectFirst("meta[itemprop=embedUrl]")?.attr("content")
            ?: ""

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
    private val streamWishExtractor by lazy { StreamWishExtractor(client, headers) }
    private val vidHideExtractor by lazy { VidHideExtractor(client, headers) }
    private val bloggerExtractor by lazy { BloggerExtractor(client) }
    private val doodExtractor by lazy { DoodExtractor(client) }

    override suspend fun getVideoList(url: String, name: String): List<Video> {
        return getVideoListSync(url, name)
    }

    private fun getVideoListSync(url: String, name: String): List<Video> {
        val lowerName = name.lowercase()
        val lowerUrl = url.lowercase()
        return runCatching {
            when {
                "streamtape" in lowerName || "streamtape" in lowerUrl ->
                    streamTapeExtractor.videoFromUrl(url)?.let(::listOf).orEmpty()

                "mp4upload" in lowerName || "mp4upload" in lowerUrl ->
                    mp4uploadExtractor.videosFromUrl(url, headers)

                "yourupload" in lowerName || "yourupload" in lowerUrl ->
                    yourUploadExtractor.videoFromUrl(url, headers)

                "ok.ru" in lowerUrl || "okru" in lowerName ->
                    okruExtractor.videosFromUrl(url)

                "streamwish" in lowerName || "streamwish" in lowerUrl || "filelions" in lowerUrl || "wishembed" in lowerUrl ->
                    streamWishExtractor.videosFromUrl(url)

                "vidhide" in lowerName || "vidhide" in lowerUrl ->
                    vidHideExtractor.videosFromUrl(url)

                "blogger" in lowerName || "blogger" in lowerUrl || "bp.blogspot" in lowerUrl ->
                    bloggerExtractor.videosFromUrl(url, headers, name)

                "dood" in lowerName || "dood" in lowerUrl || "ds2play" in lowerUrl || "doodstream" in lowerUrl ->
                    doodExtractor.videosFromUrl(url)

                "gdrive" in lowerName || "gdrive" in lowerUrl -> {
                    val gdriveUrl = when {
                        baseUrl in url -> "https:" + (url.toHttpUrlOrNull()?.queryParameter("data") ?: url)
                        else -> url
                    }
                    gdrivePlayerExtractor.videosFromUrl(gdriveUrl, "Gdrive", headers)
                }

                url.endsWith(".mp4") || url.endsWith(".m3u8") || url.contains(".mp4?") || url.contains(".m3u8?") ->
                    listOf(Video(url, name, url, headers))

                else -> {
                    Log.i("Animasu", "Unrecognized server at getVideoList => Name -> $name || URL => $url")
                    emptyList()
                }
            }
        }.getOrDefault(emptyList())
    }
}
