package eu.kanade.tachiyomi.animeextension.id.animasu

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
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.multisrc.animestream.AnimeStream
import eu.kanade.tachiyomi.network.GET
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
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

    override val animeListUrl = "$baseUrl/pencarian"

    override val dateFormatter by lazy {
        SimpleDateFormat("MMMM d, yyyy", Locale(lang))
    }

    // ============================== Popular ===============================
    override fun popularAnimeRequest(page: Int) = GET("$baseUrl/pencarian/?urutan=populer&halaman=$page")

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/pencarian/?urutan=update&halaman=$page")

    // =============================== Search ===============================
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        if (query.isNotBlank()) {
            return if (page == 1) {
                GET("$baseUrl/?s=$query")
            } else {
                GET("$baseUrl/page/$page/?s=$query")
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

        return GET(url)
    }

    override fun searchAnimeSelector() = "div.listupd div.bs, div.listupd_custompage div.bs, div.listupd article, div.bsx, div.animepost"

    override fun searchAnimeFromElement(element: Element): SAnime = SAnime.create().apply {
        val link = element.selectFirst("a") ?: return@apply
        setUrlWithoutDomain(link.attr("href"))
        title = element.selectFirst("div.tt, h2, div.title")?.text()?.trim()
            ?: link.attr("title").replace("Nonton Anime ", "").trim()
        thumbnail_url = element.selectFirst("img")?.getImageUrl()
    }

    override fun searchAnimeNextPageSelector() = "div.hpage a.r, div.pagination a.next, a.next.page-numbers"

    // ============================== Filters ===============================
    override val fetchFilters = false
    override fun getFilterList() = AnimasuFilters.FILTER_LIST

    // =========================== Anime Details ============================
    override fun getAnimeDescription(document: Document) = document.select("div.entry-content[itemprop=description] > p, div.desc > p, div.entry-content > p")
        .eachText()
        .joinToString("\n\n")

    override fun parseStatus(statusString: String?): Int = when (statusString?.trim()?.lowercase()) {
        "completed", "selesai" -> SAnime.COMPLETED
        "ongoing", "sedang tayang" -> SAnime.ONGOING
        else -> SAnime.UNKNOWN
    }

    // ============================== Episodes ==============================
    override fun episodeListSelector() = "div.eplister ul li a, div.listeps li a, ul.clstyle li a, div.bxcl ul li a, div.lstepsiode ul li a"

    override fun episodeFromElement(element: Element): SEpisode = SEpisode.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        val numText = element.selectFirst(".epl-num, .lchx, .epsleft, .eps")?.text() ?: element.text()
        val num = numText.filter { it.isDigit() || it == '.' }
        episode_number = num.toFloatOrNull() ?: 1F
        name = when {
            numText.contains("Episode", true) -> numText
            num.isNotBlank() -> "Episode $num"
            else -> element.text()
        }
        date_upload = element.selectFirst(".epl-date, .date")?.text()?.let { dateFormatter.tryParse(it) }
    }

    // ============================ Video Links =============================
    override fun videoListSelector() = "select.mirror > option[data-index], select.mirror > option[value], ul.mirror a[data-em], select#selectserver option"

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
        val lowerName = name.lowercase()
        val lowerUrl = url.lowercase()
        return when {
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
    }
}
