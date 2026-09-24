package eu.kanade.tachiyomi.animeextension.id.animeindo

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

object AnimeIndoFilters {
    open class UriPartFilter(
        displayName: String,
        private val vals: Array<Pair<String, String>>,
        defaultValue: Int = 0,
    ) : AnimeFilter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), defaultValue) {
        fun toUriPart() = vals[state].second
    }

    open class CheckBoxVal(name: String, val value: String) : AnimeFilter.CheckBox(name)

    open class CheckBoxGroup(
        name: String,
        val options: List<CheckBoxVal>,
    ) : AnimeFilter.Group<CheckBoxVal>(name, options)

    class TypeFilter : UriPartFilter(
        "Tipe",
        arrayOf(
            "Semua" to "",
            "TV Show" to "tv",
            "Movie" to "movie",
        ),
    )

    class QualityFilter : UriPartFilter(
        "Kualitas",
        arrayOf(
            "Semua" to "",
            "4K" to "4K",
            "HD" to "HD",
            "SD" to "SD",
            "CAM" to "CAM",
        ),
    )

    class ReleaseFilter : UriPartFilter(
        "Tahun Rilis",
        arrayOf(
            "Semua" to "",
            "2026" to "2026",
            "2025" to "2025",
            "2024" to "2024",
            "2023" to "2023",
            "2022" to "2022",
            "2021" to "2021",
            "2020 / Older" to "2020",
        ),
    )

    class GenreFilter : CheckBoxGroup(
        "Genre",
        listOf(
            CheckBoxVal("Action", "1"),
            CheckBoxVal("Adventure", "2"),
            CheckBoxVal("Comedy", "4"),
            CheckBoxVal("Crime", "5"),
            CheckBoxVal("Drama", "7"),
            CheckBoxVal("Family", "8"),
            CheckBoxVal("Fantasy", "9"),
            CheckBoxVal("History", "10"),
            CheckBoxVal("Horror", "11"),
            CheckBoxVal("Music", "12"),
            CheckBoxVal("Mystery", "13"),
            CheckBoxVal("Romance", "14"),
            CheckBoxVal("Science Fiction", "15"),
            CheckBoxVal("TV Movie", "16"),
            CheckBoxVal("Thriller", "17"),
            CheckBoxVal("War", "18"),
            CheckBoxVal("Western", "19"),
        ),
    )

    class CountryFilter : CheckBoxGroup(
        "Negara",
        listOf(
            CheckBoxVal("Japan", "114"),
            CheckBoxVal("China", "48"),
            CheckBoxVal("South Korea", "218"),
            CheckBoxVal("United States", "250"),
            CheckBoxVal("United Kingdom", "249"),
            CheckBoxVal("France", "78"),
            CheckBoxVal("Germany", "86"),
            CheckBoxVal("Thailand", "238"),
            CheckBoxVal("Russia", "192"),
            CheckBoxVal("Spain", "224"),
            CheckBoxVal("Italy", "112"),
            CheckBoxVal("Canada", "41"),
            CheckBoxVal("Belgium", "22"),
            CheckBoxVal("Brazil", "31"),
            CheckBoxVal("Denmark", "62"),
            CheckBoxVal("Netherlands", "160"),
            CheckBoxVal("Norway", "173"),
            CheckBoxVal("Poland", "187"),
            CheckBoxVal("Romania", "191"),
            CheckBoxVal("Sweden", "231"),
            CheckBoxVal("Turkey", "248"),
        ),
    )

    fun getFilterList(): AnimeFilterList = AnimeFilterList(
        TypeFilter(),
        QualityFilter(),
        ReleaseFilter(),
        AnimeFilter.Separator(),
        GenreFilter(),
        CountryFilter(),
    )
}
