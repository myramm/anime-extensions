package eu.kanade.tachiyomi.animeextension.id.meownime

import aniyomi.lib.bloggerextractor.BloggerExtractor
import aniyomi.lib.gdriveplayerextractor.GdrivePlayerExtractor
import aniyomi.lib.pixeldrainextractor.PixelDrainExtractor
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import keiyoushi.utils.ParsedAnimeHttpLegacySource
import keiyoushi.utils.parallelCatchingFlatMapBlocking
import keiyoushi.utils.useAsJsoup
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Meownime : ParsedAnimeHttpLegacySource() {
    override val name: String = "Meownime"

    override val baseUrl: String = "https://meownime.ltd"

    override val lang: String = "id"

    override val supportsLatest: Boolean = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")

    // ============================== Popular ===============================
    override fun popularAnimeRequest(page: Int): Request =
        if (page == 1) GET("$baseUrl/", headers) else GET("$baseUrl/page/$page/", headers)

    override fun popularAnimeSelector(): String = "article, div.post, .hentry, div.animpost, div.bsx, div.listupd div.bsx"

    override fun popularAnimeFromElement(element: Element): SAnime = SAnime.create().apply {
        val link = element.selectFirst("h2 a, h3 a, a") ?: return@apply
        setUrlWithoutDomain(link.attr("href"))
        title = element.selectFirst("h2.entry-title, h2, h3, .title, a")?.text()?.trim() ?: link.attr("title").trim()
        thumbnail_url = element.getImageUrl()
    }

    override fun popularAnimeNextPageSelector(): String? = "a.next, .pagination a.next, .nav-links a.next, nav.pagination a.next"

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
        if (page == 1) GET("$baseUrl/", headers) else GET("$baseUrl/page/$page/", headers)

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
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request =
        if (page == 1) GET("$baseUrl/?s=$query", headers) else GET("$baseUrl/page/$page/?s=$query", headers)

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

    // =========================== Anime Details ============================
    override fun animeDetailsParse(document: Document): SAnime = SAnime.create().apply {
        title = document.selectFirst("h1.entry-title, h1")?.text()?.trim().orEmpty()
        thumbnail_url = document.selectFirst("div.thumb img, .entry-content img, .wp-post-image, .post-thumbnail img, div.limage img, .thumb")?.getImageUrl()

        val infoText = document.select(".entry-content p, .info-content, .spe").text()
        status = when {
            infoText.contains("Completed", ignoreCase = true) || infoText.contains("End", ignoreCase = true) -> SAnime.COMPLETED
            infoText.contains("Ongoing", ignoreCase = true) -> SAnime.ONGOING
            else -> SAnime.UNKNOWN
        }

        genre = document.select("a[href*=/genre/], a[href*=/genres/], a[rel='category tag'], .genxed a").eachText().joinToString()

        val synopsis = document.selectFirst("div.entry-content[itemprop='description'], div.entry-content p, div.desc")?.text()?.trim().orEmpty()
        description = synopsis

        initialized = true
    }

    // ============================== Episodes ==============================
    override fun episodeListSelector(): String = throw UnsupportedOperationException()
    override fun episodeFromElement(element: Element): SEpisode = throw UnsupportedOperationException()

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.useAsJsoup()
        val episodeList = mutableListOf<SEpisode>()
        val animePath = response.request.url.encodedPath

        val boxes = document.select("div.soraddl, div.smokedl, div.download, div.soraurl, div.smokeurl, div.box-download, .dl-box")
        if (boxes.isNotEmpty()) {
            var boxIndex = 0
            for (box in boxes) {
                val titleEl = box.selectFirst(".sorattl h3, .sorattl, h3, strong, b")
                val titleText = titleEl?.text()?.trim() ?: "Episode"

                if (titleText.contains("batch", ignoreCase = true) && boxes.size > 1) {
                    continue
                }

                val numMatch = Regex("""(?i)(?:Episode|Ep|Eps)\s*(\d+(?:\.\d+)?)""").find(titleText)
                val epNum = numMatch?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: (boxIndex + 1).toFloat()

                val qualityList = mutableListOf<JSONObject>()
                for (tr in box.select("tr, p, div.dl-item")) {
                    val resEl = tr.selectFirst(".reso, .res, strong, b")
                    val res = resEl?.text()?.trim() ?: "Unknown"
                    val linksArr = JSONArray()
                    for (a in tr.select("a")) {
                        val linkHref = a.attr("href").trim()
                        val linkName = a.text().trim()
                        if (linkHref.isNotBlank() && linkHref.startsWith("http")) {
                            linksArr.put(JSONObject().apply {
                                put("name", linkName)
                                put("url", linkHref)
                            })
                        }
                    }
                    if (linksArr.length() > 0) {
                        qualityList.add(JSONObject().apply {
                            put("res", res)
                            put("links", linksArr)
                        })
                    }
                }

                if (qualityList.isNotEmpty()) {
                    val epJson = JSONObject().apply {
                        put("animePath", animePath)
                        put("title", titleText)
                        put("qualities", JSONArray(qualityList))
                    }

                    episodeList.add(SEpisode.create().apply {
                        name = titleText
                        episode_number = epNum
                        url = epJson.toString()
                        date_upload = 0L
                    })
                    boxIndex++
                }
            }
        }

        // Fallback: check paragraphs with download links in .entry-content
        if (episodeList.isEmpty()) {
            val paragraphs = document.select(".entry-content p, .entry-content div")
            var epCounter = 1F
            for (p in paragraphs) {
                val text = p.text().trim()
                val links = p.select("a[href]")
                if (links.isNotEmpty() && (text.contains("Episode", true) || text.contains("Ep ", true) || text.contains("Eps ", true) || text.contains("480p") || text.contains("720p") || text.contains("1080p"))) {
                    val numMatch = Regex("""(?i)(?:Episode|Ep|Eps)\s*(\d+(?:\.\d+)?)""").find(text)
                    val epNum = numMatch?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: epCounter
                    val linksArr = JSONArray()
                    for (a in links) {
                        val href = a.attr("href").trim()
                        if (href.startsWith("http")) {
                            linksArr.put(JSONObject().apply {
                                put("name", a.text().trim())
                                put("url", href)
                            })
                        }
                    }
                    if (linksArr.length() > 0) {
                        val epName = if (text.contains(":")) text.substringBefore(":") else "Episode ${epNum.toInt()}"
                        val epJson = JSONObject().apply {
                            put("animePath", animePath)
                            put("title", epName)
                            put("qualities", JSONArray().put(JSONObject().apply {
                                put("res", "Direct")
                                put("links", linksArr)
                            }))
                        }
                        episodeList.add(SEpisode.create().apply {
                            name = epName
                            episode_number = epNum
                            url = epJson.toString()
                        })
                        epCounter++
                    }
                }
            }
        }

        if (episodeList.isEmpty()) {
            val pageTitle = document.selectFirst("h1")?.text()?.trim() ?: "Full Anime"
            episodeList.add(SEpisode.create().apply {
                url = animePath
                name = pageTitle
                episode_number = 1F
            })
        }

        return episodeList.distinctBy { it.name }.reversed()
    }

    // ============================ Video Links =============================
    override fun videoListSelector(): String = throw UnsupportedOperationException()
    override fun videoFromElement(element: Element): Video = throw UnsupportedOperationException()

    private val pixelDrainExtractor by lazy { PixelDrainExtractor() }
    private val gdrivePlayerExtractor by lazy { GdrivePlayerExtractor(client) }
    private val bloggerExtractor by lazy { BloggerExtractor(client) }

    override fun videoListParse(response: Response): List<Video> {
        val document = response.useAsJsoup()
        val links = mutableListOf<Triple<String, String, String>>()

        document.select("div.soraddl tr, div.smokedl tr, div.download tr, .entry-content p").forEach { container ->
            val res = container.selectFirst(".reso, .res, strong, b")?.text()?.trim() ?: "Direct"
            container.select("a[href]").forEach { a ->
                val host = a.text().trim()
                val link = a.attr("href").trim()
                if (link.isNotBlank() && link.startsWith("http")) {
                    links.add(Triple(host, res, link))
                }
            }
        }

        return links.parallelCatchingFlatMapBlocking { (host, res, link) ->
            extractVideosFromLink(host, res, link)
        }.distinctBy { it.videoUrl }
    }

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        val urlStr = episode.url
        if (urlStr.startsWith("{")) {
            return runCatching {
                val json = JSONObject(urlStr)
                val qualities = json.optJSONArray("qualities") ?: JSONArray()
                val hostLinks = mutableListOf<Triple<String, String, String>>()

                for (i in 0 until qualities.length()) {
                    val qObj = qualities.getJSONObject(i)
                    val res = qObj.optString("res", "Direct")
                    val links = qObj.optJSONArray("links") ?: JSONArray()
                    for (j in 0 until links.length()) {
                        val lObj = links.getJSONObject(j)
                        val hostName = lObj.optString("name", "Server")
                        val linkUrl = lObj.optString("url", "")
                        if (linkUrl.isNotBlank()) {
                            hostLinks.add(Triple(hostName, res, linkUrl))
                        }
                    }
                }

                hostLinks.parallelCatchingFlatMapBlocking { (host, res, link) ->
                    extractVideosFromLink(host, res, link)
                }.distinctBy { it.videoUrl }
            }.getOrDefault(emptyList())
        } else {
            val resUrl = if (urlStr.startsWith("http")) urlStr else "$baseUrl$urlStr"
            return runCatching {
                val resp = client.newCall(GET(resUrl, headers)).awaitSuccess()
                videoListParse(resp)
            }.getOrDefault(emptyList())
        }
    }

    private suspend fun extractVideosFromLink(host: String, resolution: String, link: String): List<Video> {
        if (link.isBlank()) return emptyList()

        val cleanHeaders = Headers.Builder()
            .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build()

        return runCatching {
            when {
                // PixelDrain direct download / stream
                "pixeldrain" in link -> {
                    val id = Regex("""/(?:u|file|api/file)/([a-zA-Z0-9]+)""").find(link)?.groupValues?.get(1)
                    if (!id.isNullOrBlank()) {
                        val directUrl = "https://pixeldrain.com/api/file/$id?download"
                        listOf(Video(directUrl, "PixelDrain - $resolution", headers = cleanHeaders))
                    } else {
                        pixelDrainExtractor.videosFromUrl(link, prefix = "$host - $resolution - ")
                    }
                }

                // MediaFire direct download / stream
                "mediafire.com" in link -> {
                    val reqHeaders = Headers.Builder()
                        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .build()
                    val html = client.newCall(GET(link, reqHeaders)).awaitSuccess().body.string()
                    val dlUrl = Regex("""https?://download\d+\.mediafire\.com/[^\s"']+""").find(html)?.value
                        ?: Jsoup.parse(html).selectFirst("a#downloadButton, a.popsok, a[aria-label='Download file']")?.attr("href")
                    if (!dlUrl.isNullOrBlank() && dlUrl.startsWith("http")) {
                        listOf(Video(dlUrl, "MediaFire - $resolution", headers = reqHeaders))
                    } else emptyList()
                }

                // Direct video link
                link.endsWith(".mp4") || link.endsWith(".mkv") || link.contains(".mp4?") || link.contains(".mkv?") -> {
                    listOf(Video(link, "$host - $resolution", headers = cleanHeaders))
                }

                // Blogger / Google Drive
                "blogger" in link || "bp.blogspot" in link || "video.googleusercontent" in link -> {
                    bloggerExtractor.videosFromUrl(link, cleanHeaders, "$host - $resolution")
                }

                "drive.google" in link -> {
                    gdrivePlayerExtractor.videosFromUrl(link, "$host - $resolution", headers = cleanHeaders)
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

