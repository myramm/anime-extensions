package eu.kanade.tachiyomi.animeextension.id.samehadaku

import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import aniyomi.lib.bloggerextractor.BloggerExtractor
import aniyomi.lib.mp4uploadextractor.Mp4uploadExtractor
import aniyomi.lib.streamwishextractor.StreamWishExtractor
import aniyomi.lib.vidhideextractor.VidHideExtractor
import aniyomi.lib.youruploadextractor.YourUploadExtractor
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.AnimeHttpLegacySource
import keiyoushi.utils.UrlUtils
import keiyoushi.utils.bodyString
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parallelCatchingFlatMapBlocking
import keiyoushi.utils.parallelMapNotNullBlocking
import keiyoushi.utils.tryParse
import keiyoushi.utils.useAsJsoup
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class Samehadaku :
    AnimeHttpLegacySource(),
    ConfigurableAnimeSource {
    override val name: String = "Samehadaku"

    override val baseUrl: String = "https://v2.samehadaku.how"
    override val lang: String = "id"
    override val supportsLatest: Boolean = true

    private val bloggerExtractor by lazy { BloggerExtractor(client) }
    private val yourUploadExtractor by lazy { YourUploadExtractor(client) }
    private val mp4uploadExtractor by lazy { Mp4uploadExtractor(client) }
    private val preferences by getPreferencesLazy()

    // ============================== Popular ===============================

    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/daftar-anime-2/page/$page/?order=popular")

    override fun popularAnimeParse(response: Response): AnimesPage = getAnimeParse(response.asJsoup(), "div.relat > article")

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/daftar-anime-2/page/$page/?order=update")

    override fun latestUpdatesParse(response: Response): AnimesPage = getAnimeParse(response.asJsoup(), "div.relat > article")

    // ============================== Related ===============================

    override fun relatedAnimeListParse(response: Response): List<SAnime> {
        val document = response.asJsoup()
        return document.select(".rand-animesu li").mapNotNull {
            SAnime.create().apply {
                it.selectFirst("a")?.attr("href")?.let { url -> setUrlWithoutDomain(url) } ?: return@mapNotNull null
                title = it.selectFirst(".judul")?.text() ?: return@mapNotNull null
                thumbnail_url = it.selectFirst("img")?.attr("src")
            }
        }
    }

    // =============================== Search ===============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val params = SamehadakuFilters.getSearchParameters(filters)
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("daftar-anime-2")
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
            }
            addPathSegment("")
            addQueryParameter("title", query)
        }.build()
        return GET("$url${params.filter}", headers)
    }

    override fun searchAnimeParse(response: Response): AnimesPage {
        val doc = response.asJsoup()
        val searchSelector = "main.site-main.relat > article"
        return if (doc.selectFirst(searchSelector) != null) {
            getAnimeParse(doc, searchSelector)
        } else {
            getAnimeParse(doc, "div.relat > article")
        }
    }

    // ============================== Filters ===============================

    override fun getFilterList(): AnimeFilterList = SamehadakuFilters.FILTER_LIST

    override fun animeDetailsParse(response: Response): SAnime {
        val doc = response.asJsoup()
        val detail = doc.selectFirst("div.infox > div.spe")

        val extractedGenres = doc.select("div.genre-info a")
            .mapNotNull { it.text().takeIf(String::isNotBlank) }
            .joinToString().takeIf(String::isNotBlank)
            ?: detail?.selectFirst("span:has(b:contains(Genre))")?.let { genres: Element ->
                genres.select("a")
                    .mapNotNull { it.text().takeIf(String::isNotBlank) }
                    .joinToString().takeIf(String::isNotBlank)
                    ?: genres.text().substringAfter(":").trim()
            }

        return SAnime.create().apply {
            author = detail?.getInfo("Studio") ?: ""
            status = detail?.let { parseStatus(it.getInfo("Status")) } ?: SAnime.UNKNOWN

            (
                doc.selectFirst("h3.anim-detail")?.text()?.split("Detail Anime")?.getOrNull(1)
                    ?: doc.selectFirst("h2.entry-title[itemprop='partOfSeries']")?.text()?.removeSurrounding("Sinopsis Anime", "Indo")
                    ?: doc.selectFirst("h1.entry-title")?.text()?.removeSuffix("Sub Indo")
                )
                ?.trim()?.let { title = it }

            (
                doc.selectFirst("div.infoanime.widget_senction > div.thumb > img")?.attr("src")
                    ?: doc.selectFirst("div.episodeinf > div.infoanime > div.areainfo > div.thumb > img")?.attr("src")
                )
                .let { thumbnail_url = it }

            (
                doc.selectFirst("div.entry-content.entry-content-single > p")?.text()
                    ?: doc.selectFirst("div.desc > div.entry-content.entry-content-single")?.text()
                )
                .let { description = it }

            extractedGenres?.let { genre = it }
        }
    }

    // ============================== Episodes ==============================

    override fun episodeListParse(response: Response): List<SEpisode> {
        val doc = response.asJsoup()
        return doc.select("div.lstepsiode > ul > li")
            .mapNotNull {
                val episode = it.selectFirst("span.eps > a") ?: return@mapNotNull null
                SEpisode.create().apply {
                    setUrlWithoutDomain(episode.attr("href"))
                    episode_number = episode.text().trim().toFloatOrNull() ?: 1F
                    name = it.selectFirst("span.lchx > a")?.text() ?: return@mapNotNull null
                    date_upload = it.selectFirst("span.date")?.text()
                        ?.let { date -> DATE_FORMATTER.tryParse(date) }
                        ?: 0L
                }
            }
    }

    // ============================ Video Links =============================

    override fun videoListParse(response: Response): List<Video> {
        val doc = response.useAsJsoup()
        val parseUrl = response.request.url.toUrl()
        val url = "${parseUrl.protocol}://${parseUrl.host}"

        val ajaxVideos = doc.select("#server > ul > li > div")
            .parallelMapNotNullBlocking {
                runCatching { getEmbedLinks(url, it) }.getOrNull()
            }
            .parallelCatchingFlatMapBlocking { server ->
                getVideosFromEmbed(server.first, server.second)
            }

        val downloadElements = doc.select("div.download ul li, div.download-eps ul li")
        val downloadVideos = downloadElements.flatMap { li ->
            val quality = li.selectFirst("strong, b, span")?.text()?.trim() ?: "Download"
            li.select("a[href]").mapNotNull { a ->
                val server = a.text().trim()
                val href = a.attr("href").trim()
                if (href.startsWith("http")) {
                    val name = if (server.isNotBlank()) "$server ($quality)" else quality
                    Pair(name, href)
                } else null
            }
        }.parallelCatchingFlatMapBlocking { server ->
            getVideosFromEmbed(server.first, server.second)
        }

        val allVideos = ajaxVideos + downloadVideos
        return allVideos.distinctBy { it.videoUrl }
    }

    // ============================= Utilities ==============================

    override fun List<Video>.sortVideos(): List<Video> {
        val quality = preferences.getString(PREF_QUALITY_KEY, PREF_QUALITY_DEFAULT)!!
        return sortedWith(compareByDescending { it.videoTitle.contains(quality) })
    }

    private fun Element.getInfo(info: String, cut: Boolean = true): String? = selectFirst("span:has(b:contains($info))")?.text()
        ?.let {
            when {
                cut -> it.substringAfter(" ")
                else -> it
            }.trim()
        }

    private fun getAnimeParse(document: Document, query: String): AnimesPage {
        val animes = document.select(query).mapNotNull { elm ->
            SAnime.create().apply {
                elm.selectFirst("div > a")?.attr("href")?.let { setUrlWithoutDomain(it) } ?: return@mapNotNull null
                title = elm.selectFirst("div.title > h2")?.text() ?: return@mapNotNull null
                elm.selectFirst("div.content-thumb > img")?.attr("src")?.let { thumbnail_url = it }
            }
        }
        val hasNextPage = try {
            val pagination = document.selectFirst("div.pagination")!!
            val totalPage = pagination.selectFirst("span:nth-child(1)")!!.text().split(" ").last()
            val currentPage = pagination.selectFirst("span.page-numbers.current")!!.text()
            currentPage.toInt() < totalPage.toInt()
        } catch (_: Exception) {
            false
        }
        return AnimesPage(animes, hasNextPage)
    }

    private fun parseStatus(status: String?): Int = when (status?.trim()?.lowercase()) {
        "completed" -> SAnime.COMPLETED
        "ongoing" -> SAnime.ONGOING
        else -> SAnime.UNKNOWN
    }

    private suspend fun getEmbedLinks(url: String, element: Element): Pair<String, String> {
        val form = FormBody.Builder().apply {
            add("action", "player_ajax")
            add("post", element.attr("data-post"))
            add("nume", element.attr("data-nume"))
            add("type", element.attr("data-type"))
        }.build()

        val ajaxHeaders = headers.newBuilder()
            .set("User-Agent", USER_AGENT)
            .build()

        return client.newCall(POST("$url/wp-admin/admin-ajax.php", body = form, headers = ajaxHeaders))
            .awaitSuccess()
            .bodyString()
            .let {
                val link = srcRegex.find(it)?.groupValues[1]?.takeIf(String::isNotBlank)!!
                val server = element.selectFirst("span")?.text() ?: ""
                Pair(server, link)
            }
    }

    private suspend fun getVideosFromEmbed(server: String, link: String): List<Video> {
        val videoHeaders = headers.newBuilder()
            .set("User-Agent", USER_AGENT)
            .add("Referer", link)
            .build()

        val cleanHeaders = Headers.Builder()
            .add("User-Agent", USER_AGENT)
            .build()

        return runCatching {
            when {
                "mega.nz" in link -> emptyList()

                // Filedon, Uservideo, Samevideo (Inertia R2 apps)
                "filedon" in link || "uservideo" in link || "userdrive" in link || "samevideo" in link -> {
                    val r2Headers = Headers.Builder()
                        .add("User-Agent", USER_AGENT)
                        .add("Referer", link)
                        .add("Accept", "*/*")
                        .build()
                    val doc = client.newCall(GET(link, r2Headers)).awaitSuccess().useAsJsoup()
                    val dataPage = doc.selectFirst("div#app")?.attr("data-page") ?: return emptyList()
                    val json = JSONObject(dataPage)
                    val props = json.getJSONObject("props")
                    val videoUrl = props.getString("url")
                    listOf(Video(videoUrl, server, headers = r2Headers))
                }

                // Blogger video
                "blogger" in link || "bp.blogspot" in link || "video.googleusercontent" in link -> {
                    val bloggerHeaders = Headers.Builder()
                        .add("User-Agent", USER_AGENT)
                        .add("Referer", "https://www.blogger.com/")
                        .add("Accept", "*/*")
                        .build()
                    bloggerExtractor.videosFromUrl(link, bloggerHeaders, server)
                }

                // VidHide
                "vidhide" in link || "streamhide" in link -> {
                    VidHideExtractor(client, cleanHeaders).videosFromUrl(link)
                }

                // StreamWish / FileLions
                "streamwish" in link || "filelions" in link || "wishembed" in link || "wishfast" in link -> {
                    StreamWishExtractor(client, cleanHeaders).videosFromUrl(link, videoNameGen = { "$server - $it" })
                }

                // Mp4Upload
                "mp4upload" in link -> {
                    val mp4Headers = Headers.Builder()
                        .add("User-Agent", USER_AGENT)
                        .add("Referer", "https://www.mp4upload.com/")
                        .add("Accept", "*/*")
                        .build()
                    mp4uploadExtractor.videosFromUrl(link, mp4Headers)
                }

                // YourUpload
                "yourupload" in link -> {
                    val id = link.substringAfter("id=").substringBefore("&")
                    val url = if ("embed" in link) link else "https://yourupload.com/embed/$id"
                    val youruploadHeaders = Headers.Builder()
                        .add("User-Agent", USER_AGENT)
                        .add("Referer", "https://www.yourupload.com/")
                        .add("Accept", "*/*")
                        .build()
                    yourUploadExtractor.videoFromUrl(url, youruploadHeaders, server)
                }

                // Pixeldrain
                "pixeldrain" in link -> {
                    val id = Regex("""/(?:u|file)/([a-zA-Z0-9]+)""").find(link)?.groupValues?.get(1)
                    if (!id.isNullOrBlank()) {
                        val dlUrl = "https://pixeldrain.com/api/file/$id?download"
                        listOf(Video(dlUrl, "$server (PixelDrain)", headers = cleanHeaders))
                    } else emptyList()
                }

                // Krakenfiles
                "krakenfiles" in link -> {
                    val doc = client.newCall(GET(link, videoHeaders)).awaitSuccess().useAsJsoup()
                    val getUrl = doc.selectFirst("source")?.attr("src") ?: return emptyList()
                    val videoUrl = UrlUtils.fixUrl(getUrl)?.replace("&amp;", "&") ?: return emptyList()
                    listOf(Video(videoUrl, server, headers = videoHeaders))
                }

                link.contains(".mp4") || link.contains(".webm") || link.contains(".m3u8") -> {
                    val streamHeaders = Headers.Builder()
                        .add("User-Agent", USER_AGENT)
                        .add("Accept", "*/*")
                        .build()
                    listOf(Video(link, server, headers = streamHeaders))
                }

                else -> {
                    val doc = client.newCall(GET(link, videoHeaders)).awaitSuccess().useAsJsoup()
                    val videoUrl = doc.selectFirst("video source, video")?.attr("src") ?: return emptyList()
                    val finalUrl = UrlUtils.fixUrl(videoUrl) ?: return emptyList()
                    listOf(Video(finalUrl, server, headers = videoHeaders))
                }
            }
        }.getOrDefault(emptyList())
    }

    // ============================== Settings ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val videoQualityPref = ListPreference(screen.context).apply {
            summary = "%s"
            key = PREF_QUALITY_KEY
            title = PREF_QUALITY_TITLE
            entries = PREF_QUALITY_ENTRIES
            entryValues = PREF_QUALITY_ENTRIES
            setDefaultValue(PREF_QUALITY_DEFAULT)
        }
        screen.addPreference(videoQualityPref)
    }

    companion object {
        private val DATE_FORMATTER by lazy {
            SimpleDateFormat("d MMMM yyyy", Locale("id", "ID"))
        }
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

        private const val PREF_QUALITY_KEY = "preferred_quality"
        private const val PREF_QUALITY_TITLE = "Preferred quality"
        private const val PREF_QUALITY_DEFAULT = "720p"
        private val PREF_QUALITY_ENTRIES = arrayOf("1080p", "720p", "480p", "360p")

        private val srcRegex by lazy { Regex("""src\s*=\s*["']([^"']+)["']""") }
    }
}
