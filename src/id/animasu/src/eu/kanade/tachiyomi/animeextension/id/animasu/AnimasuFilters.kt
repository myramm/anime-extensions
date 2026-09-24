package eu.kanade.tachiyomi.animeextension.id.animasu

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

object AnimasuFilters {

    internal open class UriPartFilter(
        displayName: String,
        private val vals: Array<Pair<String, String>>,
    ) : AnimeFilter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    internal class OrderFilter : UriPartFilter("Urutan", ORDER_LIST)
    internal class StatusFilter : UriPartFilter("Status", STATUS_LIST)
    internal class TypeFilter : UriPartFilter("Tipe", TYPE_LIST)
    internal class GenreFilter : UriPartFilter("Genre", GENRES_LIST)

    private val ORDER_LIST = arrayOf(
        Pair("Update Terbaru", "update"),
        Pair("Populer", "populer"),
        Pair("Rilis Baru", "baru"),
        Pair("Rating", "rating"),
        Pair("Judul A-Z", "judul"),
    )

    private val STATUS_LIST = arrayOf(
        Pair("Semua", ""),
        Pair("Sedang Tayang (Ongoing)", "ongoing"),
        Pair("Selesai (Completed)", "selesai"),
        Pair("Segera Tayang (Upcoming)", "upcoming"),
    )

    private val TYPE_LIST = arrayOf(
        Pair("Semua", ""),
        Pair("TV", "TV"),
        Pair("Movie", "Movie"),
        Pair("OVA", "OVA"),
        Pair("ONA", "ONA"),
        Pair("Special", "Special"),
    )

    private val GENRES_LIST = arrayOf(
        Pair("Semua", ""),
        Pair("Aksi", "aksi"),
        Pair("Anak-Anak", "anak-anak"),
        Pair("Antariksa", "luar-angkasa"),
        Pair("Avant Garde", "avant-garde"),
        Pair("Dimensia", "dementia"),
        Pair("Donghua", "donghua"),
        Pair("Drama", "drama"),
        Pair("Ecchi", "ecchi"),
        Pair("Fantasi", "fantasi"),
        Pair("Fantasi Urban", "fantasi-urban"),
        Pair("Game", "game"),
        Pair("Gourmet", "gourmet"),
        Pair("Harem", "harem"),
        Pair("Horror", "horror"),
        Pair("Iblis", "iblis"),
        Pair("Isekai", "isekai"),
        Pair("Josei", "josei"),
        Pair("Komedi", "komedi"),
        Pair("Kriminal", "kriminal"),
        Pair("Kultivasi", "kultivasi"),
        Pair("Magic", "sihir"),
        Pair("Martial Arts", "seni-bela-diri"),
        Pair("Mecha", "mecha"),
        Pair("Militer", "militer"),
        Pair("Misteri", "misteri"),
        Pair("Musik", "musik"),
        Pair("Parodi", "parodi"),
        Pair("Petualangan", "petualangan"),
        Pair("Polisi", "polisi"),
        Pair("Psikologis", "psikologis"),
        Pair("Reinkarnasi", "reinkarnasi"),
        Pair("Romantis", "romantis"),
        Pair("Samurai", "samurai"),
        Pair("School", "sekolah"),
        Pair("Sci-Fi", "sci-fi"),
        Pair("Seinen", "seinen"),
        Pair("Shoujo", "shoujo"),
        Pair("Shounen", "shounen"),
        Pair("Slice of Life", "slice-of-life"),
        Pair("Sports", "olahraga"),
        Pair("Super Power", "kekuatan-super"),
        Pair("Supernatural", "supranatural"),
        Pair("Suspense", "suspense"),
        Pair("Vampir", "vampir"),
    )

    val FILTER_LIST
        get() = AnimeFilterList(
            OrderFilter(),
            StatusFilter(),
            TypeFilter(),
            AnimeFilter.Separator(),
            GenreFilter(),
        )
}
