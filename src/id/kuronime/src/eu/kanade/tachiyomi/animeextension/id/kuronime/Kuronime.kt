package eu.kanade.tachiyomi.animeextension.id.kuronime

import android.util.Base64
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceScreen
import aniyomi.lib.mp4uploadextractor.Mp4uploadExtractor
import aniyomi.lib.streamlareextractor.StreamlareExtractor
import aniyomi.lib.youruploadextractor.YourUploadExtractor
import eu.kanade.tachiyomi.animeextension.id.kuronime.extractors.AnimekuExtractor
import eu.kanade.tachiyomi.animeextension.id.kuronime.extractors.HxFileExtractor
import eu.kanade.tachiyomi.animeextension.id.kuronime.extractors.LinkBoxExtractor
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.ParsedAnimeHttpLegacySource
import keiyoushi.utils.getPreferencesLazy
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Locale

class Kuronime :
    ParsedAnimeHttpLegacySource(),
    ConfigurableAnimeSource {
    override val baseUrl: String = "https://kuronime.sbs"
    override val lang: String = "id"
    override val name: String = "Kuronime"
    override val supportsLatest: Boolean = true

    private val preferences by getPreferencesLazy()

    override fun animeDetailsParse(document: Document): SAnime {
        val anime = SAnime.create()
        val infodetail = document.select("div.infodetail")
        val status = parseStatus(infodetail.select("ul > li:nth-child(3)").text().replace("Status: ", ""))
        anime.title = infodetail.select("ul > li:nth-child(1)").text().replace("Judul: ", "")
        anime.genre = infodetail.select("ul > li:nth-child(2)").joinToString(", ") { it.text() }
        anime.status = status
        anime.artist = infodetail.select("ul > li:nth-child(4)").text().replace("Studio: ", "")
        anime.author = "UNKNOWN"
        anime.thumbnail_url = document.selectFirst("div.infodetail img, div.con img, div.thumb img, div.limage img, .thumb")?.getImageUrl()
        anime.description = "Synopsis: \n" + document.select("div.main-info > div.con > div.r > div > span > p").text()
        return anime
    }

    private fun parseStatus(statusString: String): Int = when (statusString.lowercase(Locale.US)) {
        "ongoing" -> SAnime.ONGOING
        "completed" -> SAnime.COMPLETED
        else -> SAnime.UNKNOWN
    }

    override fun episodeFromElement(element: Element): SEpisode {
        val episode = SEpisode.create()
        val epsNum = getNumberFromEpsString(element.select("span.lchx").text())
        episode.setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
        episode.episode_number = when {
            epsNum.isNotEmpty() -> epsNum.toFloatOrNull() ?: 1F
            else -> 1F
        }
        episode.name = element.select("span.lchx").text()

        return episode
    }

    private fun getNumberFromEpsString(epsStr: String): String = epsStr.filter { it.isDigit() }

    override fun episodeListSelector(): String = "div.bixbox.bxcl > ul > li"

    override fun latestUpdatesFromElement(element: Element): SAnime = getAnimeFromAnimeElement(element)

    private fun getAnimeFromAnimeElement(element: Element): SAnime {
        val anime = SAnime.create()
        val link = element.selectFirst("div > a, a")
        if (link != null) {
            anime.setUrlWithoutDomain(link.attr("href"))
        }

        anime.thumbnail_url = element.getImageUrl()
        anime.title = element.select("div > a > div.tt > h4, div.tt h4, h4, h2").text().trim()
        return anime
    }
    override fun latestUpdatesNextPageSelector(): String = "div.pagination > a.next"

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/anime/?page=$page&status=ongoing&sub=&order=update")

    override fun latestUpdatesSelector(): String = "div.listupd > article"

    override fun popularAnimeFromElement(element: Element): SAnime = getAnimeFromAnimeElement(element)

    override fun popularAnimeNextPageSelector(): String = "div.pagination > a.next"

    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/anime/page/$page")

    override fun popularAnimeSelector(): String = "div.listupd > article"

    override fun searchAnimeFromElement(element: Element): SAnime = getAnimeFromAnimeElement(element)

    override fun searchAnimeNextPageSelector(): String = "a.next.page-numbers"

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        // filter and stuff in v2
        return GET("$baseUrl/page/$page/?s=$query")
    }

    override fun searchAnimeSelector(): String = "div.listupd > article"

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val videoList = mutableListOf<Video>()

        val hosterSelection = preferences.getStringSet(
            "hoster_selection",
            setOf("animeku", "mp4upload", "yourupload", "streamlare", "linkbox"),
        )!!

        document.select("select.mirror > option[value]").forEach { opt ->
            val decoded = if (opt.attr("value").isEmpty()) {
                document.selectFirst("iframe")!!.attr("data-src")
            } else {
                Jsoup.parse(
                    String(Base64.decode(opt.attr("value"), Base64.DEFAULT)),
                ).select("iframe[data-src~=.]").attr("data-src")
            }

            when {
                hosterSelection.contains("animeku") && decoded.contains("animeku.org") -> {
                    videoList.addAll(AnimekuExtractor(client).getVideosFromUrl(decoded, opt.text()))
                }

                hosterSelection.contains("mp4upload") && decoded.contains("mp4upload.com") -> {
                    val videos = Mp4uploadExtractor(client).videosFromUrl(decoded, headers, suffix = " - ${opt.text()}")
                    videoList.addAll(videos)
                }

                hosterSelection.contains("yourupload") && decoded.contains("yourupload.com") -> {
                    videoList.addAll(YourUploadExtractor(client).videoFromUrl(decoded, headers, opt.text(), "Original - "))
                }

                hosterSelection.contains("streamlare") && decoded.contains("streamlare.com") -> {
                    videoList.addAll(StreamlareExtractor(client).videosFromUrl(decoded, suffix = "- " + opt.text()))
                }

                hosterSelection.contains("hxfile") && decoded.contains("hxfile.co") -> {
                    videoList.addAll(HxFileExtractor(client).getVideoFromUrl(decoded, opt.text()))
                }

                hosterSelection.contains("linkbox") && decoded.contains("linkbox.to") -> {
                    videoList.addAll(LinkBoxExtractor(client).videosFromUrl(decoded, opt.text()))
                }
            }
        }

        return videoList
    }

    override fun videoFromElement(element: Element): Video = throw UnsupportedOperationException()

    override fun videoListSelector(): String = throw UnsupportedOperationException()

    override fun List<Video>.sortVideos(): List<Video> {
        val quality = preferences.getString("preferred_quality", null)
        if (quality != null) {
            val newList = mutableListOf<Video>()
            var preferred = 0
            for (video in this) {
                if (video.videoTitle.contains(quality)) {
                    newList.add(preferred, video)
                    preferred++
                } else {
                    newList.add(video)
                }
            }
            return newList
        }
        return this
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val hostSelection = MultiSelectListPreference(screen.context).apply {
            key = "hoster_selection"
            title = "Enable/Disable Hosts"
            entries = arrayOf("Animeku", "Mp4Upload", "YourUpload", "Streamlare", "Hxfile", "Linkbox")
            entryValues = arrayOf("animeku", "mp4upload", "yourupload", "streamlare", "hxfile", "linkbox")
            setDefaultValue(setOf("animeku", "mp4upload", "yourupload", "streamlare", "linkbox"))

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putStringSet(key, newValue as Set<String>).commit()
            }
        }
        val videoQualityPref = ListPreference(screen.context).apply {
            key = "preferred_quality"
            title = "Preferred quality"
            entries = arrayOf("1080p", "720p", "480p", "360p", "HD", "SD")
            entryValues = arrayOf("1080", "720", "480", "360", "HD", "SD")
            setDefaultValue("1080")
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                val entry = entryValues[index] as String
                preferences.edit().putString(key, entry).commit()
            }
        }
        screen.addPreference(hostSelection)
        screen.addPreference(videoQualityPref)
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

