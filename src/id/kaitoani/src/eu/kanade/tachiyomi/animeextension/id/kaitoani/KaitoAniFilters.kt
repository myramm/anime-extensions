package eu.kanade.tachiyomi.animeextension.id.kaitoani

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

object KaitoAniFilters {
    open class QueryPartFilter(
        displayName: String,
        val vals: Array<Pair<String, String>>,
    ) : AnimeFilter.Select<String>(
        displayName,
        vals.map { it.first }.toTypedArray(),
    ) {
        fun toQueryPart(name: String): String {
            val v = vals[state].second
            return if (v.isNotBlank()) "&$name=$v" else ""
        }
    }

    open class CheckBoxFilterList(name: String, values: List<AnimeFilter.CheckBox>) : AnimeFilter.Group<AnimeFilter.CheckBox>(name, values)
    private class CheckBoxVal(name: String, state: Boolean = false) : AnimeFilter.CheckBox(name, state)

    private inline fun <reified R> AnimeFilterList.asQueryPart(name: String): String = (this.getFirst<R>() as? QueryPartFilter)?.toQueryPart(name).orEmpty()

    private inline fun <reified R> AnimeFilterList.getFirst(): R? = this.filterIsInstance<R>().firstOrNull()

    private inline fun <reified R> AnimeFilterList.parseCheckbox(
        options: Array<Pair<String, String>>,
        name: String,
    ): String {
        val group = this.getFirst<R>() as? CheckBoxFilterList ?: return ""
        return group.state
            .mapNotNull { checkbox ->
                if (checkbox.state) {
                    options.find { it.first == checkbox.name }?.second
                } else null
            }.joinToString("&$name[]=").let {
                if (it.isBlank()) "" else "&$name[]=$it"
            }
    }

    class OrderFilter : QueryPartFilter("Urutkan", FiltersData.ORDER)
    class StatusFilter : QueryPartFilter("Status", FiltersData.STATUS)
    class TypeFilter : QueryPartFilter("Tipe", FiltersData.TYPE)
    class SubFilter : QueryPartFilter("Sub", FiltersData.SUB)

    class GenreFilter : CheckBoxFilterList(
        "Genre",
        FiltersData.GENRE.map { CheckBoxVal(it.first, false) },
    )

    class StudioFilter : CheckBoxFilterList(
        "Studio",
        FiltersData.STUDIO.map { CheckBoxVal(it.first, false) },
    )

    class SeasonFilter : CheckBoxFilterList(
        "Season",
        FiltersData.SEASON.map { CheckBoxVal(it.first, false) },
    )

    val FILTER_LIST
        get() = AnimeFilterList(
            AnimeFilter.Header("Catatan: Filter diabaikan jika kolom pencarian diisi"),
            OrderFilter(),
            StatusFilter(),
            TypeFilter(),
            SubFilter(),
            AnimeFilter.Separator(),
            GenreFilter(),
            AnimeFilter.Separator(),
            StudioFilter(),
            AnimeFilter.Separator(),
            SeasonFilter(),
        )

    internal fun getSearchParameters(filters: AnimeFilterList): String {
        if (filters.isEmpty()) return "order="
        val sb = StringBuilder()
        sb.append(filters.asQueryPart<OrderFilter>("order"))
        sb.append(filters.asQueryPart<StatusFilter>("status"))
        sb.append(filters.asQueryPart<TypeFilter>("type"))
        sb.append(filters.asQueryPart<SubFilter>("sub"))
        sb.append(filters.parseCheckbox<GenreFilter>(FiltersData.GENRE, "genre"))
        sb.append(filters.parseCheckbox<StudioFilter>(FiltersData.STUDIO, "studio"))
        sb.append(filters.parseCheckbox<SeasonFilter>(FiltersData.SEASON, "season"))
        return sb.toString().removePrefix("&")
    }

    private object FiltersData {
        val ORDER = arrayOf(
            Pair("Default (Baru Diupdate)", ""),
            Pair("Judul (A-Z)", "title"),
            Pair("Judul (Z-A)", "titlereverse"),
            Pair("Update Terbaru", "update"),
            Pair("Baru Ditambahkan", "latest"),
            Pair("Populer", "popular"),
            Pair("Rating Tertinggi", "rating"),
        )

        val STATUS = arrayOf(
            Pair("Semua", ""),
            Pair("Ongoing", "ongoing"),
            Pair("Completed", "completed"),
            Pair("Upcoming", "upcoming"),
            Pair("Hiatus", "hiatus"),
        )

        val TYPE = arrayOf(
            Pair("Semua", ""),
            Pair("TV Series", "tv"),
            Pair("OVA", "ova"),
            Pair("Movie", "movie"),
            Pair("Live Action", "live action"),
            Pair("Special", "special"),
            Pair("BD", "bd"),
            Pair("ONA", "ona"),
            Pair("Music", "music"),
        )

        val SUB = arrayOf(
            Pair("Semua", ""),
            Pair("Sub (Indo)", "sub"),
            Pair("Dub", "dub"),
            Pair("RAW", "raw"),
        )

        val GENRE = arrayOf(
            Pair("3D", "3d"),
            Pair("Ahegao", "ahegao"),
            Pair("Anal", "anal"),
            Pair("Anthropomorphic", "anthropomorphic"),
            Pair("Armpit", "armpit"),
            Pair("Big Oppai", "big-oppai"),
            Pair("Blowjob", "blowjob"),
            Pair("Bondage", "bondage"),
            Pair("Cosplay", "cosplay"),
            Pair("Creampie", "creampie"),
            Pair("Dark Skin", "dark-skin"),
            Pair("Drama", "drama"),
            Pair("Elf", "elf"),
            Pair("Erotica", "erotica"),
            Pair("Exhibitionist", "exhibitionist"),
            Pair("Fantasy", "fantasy"),
            Pair("Femdom", "femdom"),
            Pair("Footjob", "footjob"),
            Pair("Forced", "forced"),
            Pair("Futanari", "futanari"),
            Pair("Gangbang", "gangbang"),
            Pair("Handjob", "handjob"),
            Pair("Harem", "harem"),
            Pair("Hentai", "hentai"),
            Pair("Housewife", "housewife"),
            Pair("Incest", "incest"),
            Pair("Intercrural", "intercrural"),
            Pair("Loli", "loli"),
            Pair("Magic", "magic"),
            Pair("Maid", "maid"),
            Pair("Male Monster", "male-monster"),
            Pair("Masturbation", "masturbation"),
            Pair("Milf", "milf"),
            Pair("Monster", "monster"),
            Pair("NTR", "ntr"),
            Pair("Nurse", "nurse"),
            Pair("Office", "office"),
            Pair("Onsen", "onsen"),
            Pair("Oral", "oral"),
            Pair("Paizuri", "paizuri"),
            Pair("Pregnant", "pregnant"),
            Pair("Rape", "rape"),
            Pair("Romance", "romance"),
            Pair("School", "school"),
            Pair("School Girl", "school-girl"),
            Pair("Sex Toys", "sex-toys"),
            Pair("Shibari", "shibari"),
            Pair("Shota", "shota"),
            Pair("Shoujo", "shoujo"),
            Pair("Stocking", "stocking"),
            Pair("Succubus", "succubus"),
            Pair("Swimsuit", "swimsuit"),
            Pair("Tentacles", "tentacles"),
            Pair("Threesome", "threesome"),
            Pair("Uncensored", "uncen"),
            Pair("Vanilla", "vanilla"),
            Pair("Virgin", "virgin"),
            Pair("Yuri", "yuri"),
        )

        val STUDIO = arrayOf(
            Pair("Anime Antenna Iinkai", "anime-antenna-iinkai"),
            Pair("Blue bread", "blue-bread"),
            Pair("Blue Cat", "blue-cat"),
            Pair("BreakBottle", "breakbottle"),
            Pair("Circle Tribute", "circle-tribute"),
            Pair("Collaboration Works", "collaboration-works"),
            Pair("Concept Films", "concept-films"),
            Pair("Dream Entertainment", "dream-entertainment"),
            Pair("Elias", "elias"),
            Pair("Flavors Soft", "flavors-soft"),
            Pair("G-Lam", "g-lam"),
            Pair("Kazuki Production", "kazuki-production"),
            Pair("Lyrics", "lyrics"),
            Pair("Majin", "majin"),
            Pair("Mousou Senka", "mousou-senka"),
            Pair("New Generation", "new-generation"),
            Pair("Nur", "nur"),
            Pair("Office TakeOut", "office-takeout"),
            Pair("Passione", "passione"),
            Pair("Peak Hunt", "peak-hunt"),
            Pair("Piko Studio", "piko-studio"),
            Pair("Poly Animation", "poly-animation"),
            Pair("PoRO", "poro"),
            Pair("Queen Bee", "queen-bee"),
            Pair("Rabbit Gate", "rabbit-gate"),
            Pair("Raiose", "raiose"),
            Pair("Ryuu M's", "ryuu-ms"),
            Pair("Schoolzone", "schoolzone"),
            Pair("Seven", "seven"),
            Pair("Shinkuukan", "shinkuukan"),
            Pair("Shion", "shion"),
            Pair("Shura", "shura"),
            Pair("Silver", "silver"),
            Pair("Studio 1st", "studio-1st"),
            Pair("Studio 9 Maiami", "studio-9-maiami"),
            Pair("Studio Eromatick", "studio-eromatick"),
            Pair("Studio Fantasia", "studio-fantasia"),
            Pair("Studio Hokiboshi", "studio-hokiboshi"),
            Pair("Studio LEO", "studio-leo"),
            Pair("Studio March", "studio-march"),
            Pair("Studio Soul", "studio-soul"),
            Pair("Suzuki Mirano", "suzuki-mirano"),
            Pair("T-Rex", "t-rex"),
            Pair("Tsubo Production", "tsubo-production"),
            Pair("XTER", "xter"),
            Pair("Y.O.U.C", "y-o-u-c"),
        )

        val SEASON = arrayOf(
            Pair("Fall 2003", "fall-2003"),
            Pair("Fall 2004", "fall-2004"),
            Pair("Fall 2005", "fall-2005"),
            Pair("Fall 2007", "fall-2007"),
            Pair("Fall 2009", "fall-2009"),
            Pair("Fall 2010", "fall-2010"),
            Pair("Fall 2011", "fall-2011"),
            Pair("Fall 2014", "fall-2014"),
            Pair("Fall 2016", "fall-2016"),
            Pair("Fall 2021", "fall-2021"),
            Pair("Fall 2022", "fall-2022"),
            Pair("Fall 2023", "fall-2023"),
            Pair("Fall 2024", "fall-2024"),
            Pair("Fall 2025", "fall-2025"),
            Pair("Spring 2002", "spring-2002"),
            Pair("Spring 2004", "spring-2004"),
            Pair("Spring 2005", "spring-2005"),
            Pair("Spring 2006", "spring-2006"),
            Pair("Spring 2011", "spring-2011"),
            Pair("Spring 2012", "spring-2012"),
            Pair("Spring 2013", "spring-2013"),
            Pair("Spring 2014", "spring-2014"),
            Pair("Spring 2015", "spring-2015"),
            Pair("Spring 2020", "spring-2020"),
            Pair("Spring 2021", "spring-2021"),
            Pair("Spring 2023", "spring-2023"),
            Pair("Spring 2024", "spring-2024"),
            Pair("Spring 2025", "spring-2025"),
            Pair("Spring 2026", "spring-2026"),
            Pair("Summer 2002", "summer-2002"),
            Pair("Summer 2003", "summer-2003"),
            Pair("Summer 2005", "summer-2005"),
            Pair("Summer 2006", "summer-2006"),
            Pair("Summer 2009", "summer-2009"),
            Pair("Summer 2010", "summer-2010"),
            Pair("Summer 2013", "summer-2013"),
            Pair("Summer 2017", "summer-2017"),
            Pair("Summer 2020", "summer-2020"),
            Pair("Summer 2022", "summer-2022"),
            Pair("Summer 2023", "summer-2023"),
            Pair("Summer 2024", "summer-2024"),
            Pair("Summer 2025", "summer-2025"),
            Pair("Summer 2026", "summer-2026"),
            Pair("Winter 2003", "winter-2003"),
            Pair("Winter 2006", "winter-2006"),
            Pair("Winter 2007", "winter-2007"),
            Pair("Winter 2009", "winter-2009"),
            Pair("Winter 2010", "winter-2010"),
            Pair("Winter 2011", "winter-2011"),
            Pair("Winter 2012", "winter-2012"),
            Pair("Winter 2013", "winter-2013"),
            Pair("Winter 2014", "winter-2014"),
            Pair("Winter 2015", "winter-2015"),
            Pair("Winter 2017", "winter-2017"),
            Pair("Winter 2019", "winter-2019"),
            Pair("Winter 2020", "winter-2020"),
            Pair("Winter 2021", "winter-2021"),
            Pair("Winter 2022", "winter-2022"),
            Pair("Winter 2023", "winter-2023"),
            Pair("Winter 2024", "winter-2024"),
            Pair("Winter 2025", "winter-2025"),
            Pair("Winter 2026", "winter-2026"),
        )
    }
}
