package eu.kanade.tachiyomi.animeextension.id.astronime

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

object AstronimeFilters {
    open class UriPartFilter(
        displayName: String,
        private val vals: Array<Pair<String, String>>,
        defaultValue: Int = 0,
    ) : AnimeFilter.Select<String>(
        displayName,
        vals.map { it.first }.toTypedArray(),
        defaultValue,
    ) {
        fun toUriPart(): String = vals[state].second
    }

    class GenreFilter : UriPartFilter("Genre", GENRES)
    class StatusFilter : UriPartFilter("Status", STATUS)
    class TypeFilter : UriPartFilter("Tipe", TYPES)
    class OrderFilter : UriPartFilter("Urutkan", ORDERS)

    val FILTER_LIST: AnimeFilterList
        get() = AnimeFilterList(
            AnimeFilter.Header("Catatan: Filter di bawah digunakan saat pencarian teks kosong"),
            GenreFilter(),
            StatusFilter(),
            TypeFilter(),
            OrderFilter(),
        )

    fun getSearchParameters(filters: AnimeFilterList): String {
        var genre = ""
        var status = ""
        var type = ""
        var order = ""

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> genre = filter.toUriPart()
                is StatusFilter -> status = filter.toUriPart()
                is TypeFilter -> type = filter.toUriPart()
                is OrderFilter -> order = filter.toUriPart()
                else -> {}
            }
        }

        return buildString {
            if (genre.isNotBlank()) append("genre%5B%5D=$genre&")
            if (status.isNotBlank()) append("status=$status&")
            if (type.isNotBlank()) append("type=$type&")
            if (order.isNotBlank()) append("order=$order&")
        }.removeSuffix("&")
    }

    private val GENRES = arrayOf(
        Pair("Semua", ""),
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
        Pair("Isekai", "isekai"),
        Pair("Josei", "josei"),
        Pair("Magic", "magic"),
        Pair("Martial Arts", "martial-arts"),
        Pair("Mecha", "mecha"),
        Pair("Military", "military"),
        Pair("Music", "music"),
        Pair("Mystery", "mystery"),
        Pair("Parody", "parody"),
        Pair("Psychological", "psychological"),
        Pair("Romance", "romance"),
        Pair("Samurai", "samurai"),
        Pair("School", "school"),
        Pair("Sci-Fi", "sci-fi"),
        Pair("Seinen", "seinen"),
        Pair("Shoujo", "shoujo"),
        Pair("Shounen", "shounen"),
        Pair("Slice of Life", "slice-of-life"),
        Pair("Space", "space"),
        Pair("Sports", "sports"),
        Pair("Super Power", "super-power"),
        Pair("Supernatural", "supernatural"),
        Pair("Thriller", "thriller"),
        Pair("Vampire", "vampire"),
    )

    private val STATUS = arrayOf(
        Pair("Semua", ""),
        Pair("Ongoing", "ongoing"),
        Pair("Completed", "completed"),
    )

    private val TYPES = arrayOf(
        Pair("Semua", ""),
        Pair("TV", "tv"),
        Pair("Movie", "movie"),
        Pair("OVA", "ova"),
        Pair("ONA", "ona"),
        Pair("Special", "special"),
    )

    private val ORDERS = arrayOf(
        Pair("Default", ""),
        Pair("Terbaru Diupdate", "update"),
        Pair("Populer", "popular"),
        Pair("A-Z", "title"),
        Pair("Rating", "rating"),
        Pair("Rilis Baru", "latest"),
    )
}
