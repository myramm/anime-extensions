package eu.kanade.tachiyomi.animeextension.id.minioppai

import aniyomi.lib.gdriveplayerextractor.GdrivePlayerExtractor
import eu.kanade.tachiyomi.animeextension.id.minioppai.extractors.MiniOppaiExtractor
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.multisrc.animestream.AnimeStream
import eu.kanade.tachiyomi.network.GET
import keiyoushi.utils.tryParse
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class MiniOppai :
    AnimeStream(
        "id",
        "MiniOppai",
        "https://minioppai.org",
    ) {
    override fun headersBuilder() = super.headersBuilder().add("Referer", "$baseUrl/")

    override val animeListUrl = "$baseUrl/anime-list"

    override val dateFormatter by lazy {
        SimpleDateFormat("MMMM d, yyyy", Locale(lang))
    }

    // ============================== Popular ===============================
    override fun popularAnimeRequest(page: Int) = GET("$animeListUrl/page/$page/?order=popular")

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int) = GET("$animeListUrl/page/$page/?order=update")

    // ============================== Episodes ==============================
    override fun episodeListSelector() = "div.eplister > ul > li > a, div.epsdlist > ul > li > a, div.listeps ul li a"

    override fun episodeFromElement(element: Element): SEpisode = SEpisode.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        val numText = element.selectFirst(".epl-num")?.text() ?: element.text().trim()
        val num = numText.substringAfterLast(" ")
        episode_number = num.toFloatOrNull() ?: 1F
        name = when {
            numText.contains("OVA", true) -> "OVA $num"
            numText.isNotBlank() -> numText
            else -> "Episode $num"
        }
        element.selectFirst(".epl-sub")?.text()?.let { scanlator = it }
        date_upload = element.selectFirst(".epl-date")?.text().let { dateFormatter.tryParse(it) } ?: 0L
    }

    // ============================ Video Links =============================
    override suspend fun getVideoList(url: String, name: String): List<Video> {
        if (url.isBlank()) return emptyList()

        return when {
            "gdriveplayer" in url -> {
                val playerUrl = buildString {
                    val data = url.toHttpUrl().queryParameter("data")
                        ?: return emptyList()
                    if (data.startsWith("//")) append("https:")
                    append(data)
                }
                val cleanHeaders = Headers.Builder()
                    .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .build()
                GdrivePlayerExtractor(client).videosFromUrl(playerUrl, name, cleanHeaders)
            }

            "streampai" in url || "paistream" in url || "stream" in url ->
                MiniOppaiExtractor(client).videosFromUrl(url, headers)

            url.endsWith(".mp4") || url.endsWith(".m3u8") || url.contains(".mp4?") || url.contains(".m3u8?") -> {
                val streamHeaders = Headers.Builder()
                    .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .build()
                listOf(Video(url, if (name.isNotBlank()) name else "Direct", headers = streamHeaders))
            }

            else -> {
                val videos = MiniOppaiExtractor(client).videosFromUrl(url, headers)
                if (videos.isNotEmpty()) {
                    videos
                } else {
                    val streamHeaders = Headers.Builder()
                        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .build()
                    listOf(Video(url, if (name.isNotBlank()) name else "Stream", headers = streamHeaders))
                }
            }
        }
    }

    // =========================== Anime Details ============================
    override fun getAnimeDescription(document: Document) = document.select("div.entry-content > p").eachText().joinToString("\n")

    override fun animeDetailsParse(document: Document) = super.animeDetailsParse(document).apply {
        title = title.substringBefore("Episode").substringBefore(" OVA ")
    }

    // =============================== Search ===============================
    override fun searchAnimeSelector() = "div.latest article a.tip, div.listupd article a.tip, article.bs a.tip, div.listupd article.bsx a"

    override fun searchAnimeFromElement(element: Element): SAnime = SAnime.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        title = element.selectFirst("h2.entry-title, div.title, div.tt")?.text()?.trim() ?: element.attr("title").trim()
        thumbnail_url = element.selectFirst("img")?.getImageUrl()
    }

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val params = MiniOppaiFilters.getSearchParameters(filters)
        return if (query.isNotEmpty()) {
            GET("$baseUrl/page/$page/?s=$query")
        } else {
            val multiString = listOf(
                params.genres,
                params.countries,
                params.qualities,
                params.year,
                params.status,
            ).filter(String::isNotBlank).joinToString("&").let {
                when {
                    it.isBlank() -> it
                    else -> "&$it"
                }
            }

            GET("$animeListUrl/page/$page/?order=${params.order}$multiString")
        }
    }

    // ============================== Filters ===============================
    override val fetchFilters = false
    override fun getFilterList() = MiniOppaiFilters.FILTER_LIST

    // ============================= Utilities ==============================
    override fun Element.getInfo(text: String): String? = selectFirst("li:has(b:contains($text))")
        ?.selectFirst("span.colspan")
        ?.text()

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

