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

    class OrderFilter : UriPartFilter("Urutkan", ORDERS)
    class StatusFilter : UriPartFilter("Status", STATUS)
    class TypeFilter : UriPartFilter("Tipe", TYPES)
    class GenreFilter : UriPartFilter("Genre", GENRES)

    val FILTER_LIST: AnimeFilterList
        get() = AnimeFilterList(
            AnimeFilter.Header("Catatan: Filter di bawah digunakan saat pencarian teks kosong"),
            OrderFilter(),
            StatusFilter(),
            TypeFilter(),
            GenreFilter(),
        )

    fun getSearchParameters(filters: AnimeFilterList): String {
        var order = "popular"
        var status = ""
        var type = ""
        var genre = ""

        filters.forEach { filter ->
            when (filter) {
                is OrderFilter -> order = filter.toUriPart()
                is StatusFilter -> status = filter.toUriPart()
                is TypeFilter -> type = filter.toUriPart()
                is GenreFilter -> genre = filter.toUriPart()
                else -> {}
            }
        }

        return buildString {
            append("title=&")
            append("order=$order&")
            append("status=$status&")
            append("type=$type")
            if (genre.isNotBlank()) append("&genre%5B%5D=$genre")
        }
    }

    private val ORDERS = arrayOf(
        Pair("Populer", "popular"),
        Pair("Most Favorite", "favorite"),
        Pair("Terbaru Diupdate", "update"),
        Pair("Rilis Baru (Added)", "latest"),
        Pair("Rating", "rating"),
    )

    private val STATUS = arrayOf(
        Pair("Semua", ""),
        Pair("Currently Airing", "Currently Airing"),
        Pair("Finished Airing", "Finished Airing"),
    )

    private val TYPES = arrayOf(
        Pair("Semua", ""),
        Pair("TV", "TV"),
        Pair("Movie", "Movie"),
        Pair("OVA", "OVA"),
        Pair("ONA", "ONA"),
        Pair("Special", "Special"),
    )

    private val GENRES = arrayOf(
        Pair("Semua", ""),
        Pair("Action", "action"),
        Pair("Adult Cast", "adult-cast"),
        Pair("Adventure", "adventure"),
        Pair("Animasi", "animasi"),
        Pair("Anthropomorphic", "anthropomorphic"),
        Pair("Avant Garde", "avant-garde"),
        Pair("Award Winning", "award-winning"),
        Pair("Boys Love", "boys-love"),
        Pair("CGDCT", "cgdct"),
        Pair("Childcare", "childcare"),
        Pair("Combat Sports", "combat-sports"),
        Pair("Comedy", "comedy"),
        Pair("Crossdressing", "crossdressing"),
        Pair("Delinquents", "delinquents"),
        Pair("Detective", "detective"),
        Pair("Drama", "drama"),
        Pair("Ecchi", "ecchi"),
        Pair("Educational", "educational"),
        Pair("Fantasy", "fantasy"),
        Pair("Gag Humor", "gag-humor"),
        Pair("Gore", "gore"),
        Pair("Gourmet", "gourmet"),
        Pair("Harem", "harem"),
        Pair("Historical", "historical"),
        Pair("Horror", "horror"),
        Pair("Idols (Female)", "idols-female"),
        Pair("Idols (Male)", "idols-male"),
        Pair("Isekai", "isekai"),
        Pair("Iyashikei", "iyashikei"),
        Pair("Josei", "josei"),
        Pair("Kids", "kids"),
        Pair("Love Polygon", "love-polygon"),
        Pair("Love Status Quo", "love-status-quo"),
        Pair("Magical Sex Shift", "magical-sex-shift"),
        Pair("Mahou Shoujo", "mahou-shoujo"),
        Pair("Martial Arts", "martial-arts"),
        Pair("Mecha", "mecha"),
        Pair("Medical", "medical"),
        Pair("Military", "military"),
        Pair("Music", "music"),
        Pair("Mythology", "mythology"),
        Pair("Organized Crime", "organized-crime"),
        Pair("Otaku Culture", "otaku-culture"),
        Pair("Parody", "parody"),
        Pair("Performing Arts", "performing-arts"),
        Pair("Pets", "pets"),
        Pair("Psychological", "psychological"),
        Pair("Racing", "racing"),
        Pair("Reincarnation", "reincarnation"),
        Pair("Reverse Harem", "reverse-harem"),
        Pair("Romance", "romance"),
        Pair("Romantic Subtext", "romantic-subtext"),
        Pair("Samurai", "samurai"),
        Pair("School", "school"),
        Pair("Sci-Fi", "sci-fi"),
        Pair("Seinen", "seinen"),
        Pair("Shoujo", "shoujo"),
        Pair("Shounen", "shounen"),
        Pair("Showbiz", "showbiz"),
        Pair("Slice of Life", "slice-of-life"),
        Pair("Space", "space"),
        Pair("Sports", "sports"),
        Pair("Strategy Game", "strategy-game"),
        Pair("Super Power", "super-power"),
        Pair("Supernatural", "supernatural"),
        Pair("Survival", "survival"),
        Pair("Suspense", "suspense"),
        Pair("Team Sports", "team-sports"),
        Pair("Time Travel", "time-travel"),
        Pair("Urban Fantasy", "urban-fantasy"),
        Pair("Vampire", "vampire"),
        Pair("Video Game", "video-game"),
        Pair("Visual Arts", "visual-arts"),
        Pair("Workplace", "workplace"),
    )
}
