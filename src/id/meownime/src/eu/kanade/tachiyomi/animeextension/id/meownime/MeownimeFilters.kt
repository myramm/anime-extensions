package eu.kanade.tachiyomi.animeextension.id.meownime

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

object MeownimeFilters {
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

    class StatusFilter : UriPartFilter("Status", STATUS)
    class GenreFilter : UriPartFilter("Genre", GENRES)
    class CategoryFilter : UriPartFilter("Kategori", CATEGORIES)

    val FILTER_LIST: AnimeFilterList
        get() = AnimeFilterList(
            AnimeFilter.Header("Catatan: Filter di bawah digunakan saat pencarian teks kosong"),
            StatusFilter(),
            GenreFilter(),
            CategoryFilter(),
        )

    fun getSearchUrl(baseUrl: String, page: Int, filters: AnimeFilterList): String {
        var status = ""
        var genre = ""
        var category = ""

        filters.forEach { filter ->
            when (filter) {
                is StatusFilter -> status = filter.toUriPart()
                is GenreFilter -> genre = filter.toUriPart()
                is CategoryFilter -> category = filter.toUriPart()
                else -> {}
            }
        }

        val basePath = when {
            status.isNotEmpty() -> status
            genre.isNotEmpty() -> genre
            category.isNotEmpty() -> category
            else -> "status/completed"
        }

        return if (page == 1) {
            "$baseUrl/$basePath/"
        } else {
            "$baseUrl/$basePath/page/$page/"
        }
    }

    private val STATUS = arrayOf(
        Pair("Semua", ""),
        Pair("Completed", "status/completed"),
        Pair("Ongoing", "status/ongoing"),
    )

    private val CATEGORIES = arrayOf(
        Pair("Semua", ""),
        Pair("Anime Batch", "category/anime-batch"),
        Pair("Movie", "category/movie"),
        Pair("Live Action", "category/live-action"),
        Pair("Donghua", "category/donghua"),
    )

    private val GENRES = arrayOf(
        Pair("Semua", ""),
        Pair("Action", "genres/action"),
        Pair("Adventure", "genres/adventure"),
        Pair("Comedy", "genres/comedy"),
        Pair("Demons", "genres/demons"),
        Pair("Drama", "genres/drama"),
        Pair("Ecchi", "genres/ecchi"),
        Pair("Fantasy", "genres/fantasy"),
        Pair("Game", "genres/game"),
        Pair("Harem", "genres/harem"),
        Pair("Historical", "genres/historical"),
        Pair("Horror", "genres/horror"),
        Pair("Isekai", "genres/isekai"),
        Pair("Josei", "genres/josei"),
        Pair("Magic", "genres/magic"),
        Pair("Martial Arts", "genres/martial-arts"),
        Pair("Mecha", "genres/mecha"),
        Pair("Military", "genres/military"),
        Pair("Music", "genres/music"),
        Pair("Mystery", "genres/mystery"),
        Pair("Parody", "genres/parody"),
        Pair("Psychological", "genres/psychological"),
        Pair("Romance", "genres/romance"),
        Pair("Samurai", "genres/samurai"),
        Pair("School", "genres/school"),
        Pair("Sci-Fi", "genres/sci-fi"),
        Pair("Seinen", "genres/seinen"),
        Pair("Shoujo", "genres/shoujo"),
        Pair("Shounen", "genres/shounen"),
        Pair("Slice of Life", "genres/slice-of-life"),
        Pair("Space", "genres/space"),
        Pair("Sports", "genres/sports"),
        Pair("Super Power", "genres/super-power"),
        Pair("Supernatural", "genres/supernatural"),
        Pair("Thriller", "genres/thriller"),
        Pair("Vampire", "genres/vampire"),
    )
}
