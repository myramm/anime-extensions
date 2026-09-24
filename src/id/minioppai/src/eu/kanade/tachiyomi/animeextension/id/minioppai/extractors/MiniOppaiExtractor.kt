package eu.kanade.tachiyomi.animeextension.id.minioppai.extractors

import android.util.Base64
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.lib.unpacker.Unpacker
import okhttp3.Headers
import okhttp3.OkHttpClient
import java.net.URLDecoder

class MiniOppaiExtractor(private val client: OkHttpClient) {
    fun videosFromUrl(url: String, headers: Headers): List<Video> {
        val playerHeaders = headers.newBuilder()
            .set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build()

        val fullUrl = if (url.startsWith("//")) "https:$url" else url
        val playerDoc = runCatching {
            client.newCall(GET(fullUrl, playerHeaders)).execute().asJsoup()
        }.getOrNull() ?: return emptyList()

        val scriptData = playerDoc.selectFirst("script:containsData(eval):containsData(p,a,c,k,e,d)")
            ?.data()
            ?.let(Unpacker::unpack)
            ?.takeIf(String::isNotBlank)
            ?: playerDoc.selectFirst("script:containsData(sources)")?.data().orEmpty()

        if (scriptData.isBlank()) return emptyList()

        val baseUrl = "https://" + fullUrl.substringAfter("//").substringBefore("/")

        val subs = scriptData.getItems("\"tracks\"", baseUrl) { subUrl, label ->
            Track(subUrl, label)
        }

        val videoHeaders = Headers.Builder()
            .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .add("Referer", fullUrl)
            .build()

        val videos = mutableListOf<Video>()

        scriptData.getItems("sources", baseUrl) { videoUrl, quality ->
            val videoQuality = "MiniOppai - $quality"
            val rawB64 = Regex("""/stream/\d+/([A-Za-z0-9%_-]+={0,2})""").find(videoUrl)?.groupValues?.get(1)
            if (!rawB64.isNullOrBlank()) {
                val directUrl = runCatching {
                    val decoded = String(Base64.decode(rawB64, Base64.DEFAULT), Charsets.UTF_8)
                    URLDecoder.decode(decoded, "UTF-8")
                }.getOrNull()
                if (!directUrl.isNullOrBlank() && directUrl.startsWith("http")) {
                    videos.add(Video(directUrl, "$videoQuality (Direct)", headers = videoHeaders, subtitleTracks = subs))
                }
            }
            videos.add(Video(videoUrl, videoQuality, headers = videoHeaders, subtitleTracks = subs))
        }

        return videos.filterNot { it.videoUrl.contains("/uploads/unavailable.mp4") }
    }

    private fun <T> String.getItems(key: String, baseUrl: String, transformer: (String, String) -> T): List<T> =
        substringAfter("$key:[", "")
            .takeIf { it.isNotEmpty() }
            ?.substringBefore("]")
            ?.split("{")
            ?.drop(1)
            ?.mapNotNull {
                val file = it.extractKey("file")
                if (file.isBlank()) return@mapNotNull null
                val url = if (file.startsWith("http")) file else "$baseUrl$file"
                val label = it.extractKey("label").ifEmpty { "Default" }
                transformer(url, label)
            }
            ?: emptyList()

    private fun String.extractKey(key: String): String =
        substringAfter(key, "")
            .takeIf { it.isNotEmpty() }
            ?.substringBefore("}")
            ?.substringBefore(",")
            ?.substringAfter(":")
            ?.trim()
            ?.trim('"', '\'')
            .orEmpty()
}
