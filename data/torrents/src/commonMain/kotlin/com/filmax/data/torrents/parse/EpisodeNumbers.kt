package com.filmax.data.torrents.parse

/** Сезон (если он есть в имени файла или папки) и номер серии. */
internal data class EpisodeNumber(val season: Int?, val episode: Int)

/**
 * Номер серии по пути файла внутри раздачи. Встречается всё: «Show.S02E05.1080p.mkv», «2x05»,
 * «Серия 05», «05. Название», «Show - 05», а сезон бывает только в имени папки («Season 2»,
 * «Сезон 2», «S02»). Тот же разбор — у моста TorrServer → Jellyfin на домашнем сервере.
 */
internal object EpisodeNumbers {

    fun parse(path: String): EpisodeNumber? {
        val stem = fileStem(path)
        val both = SEASON_AND_EPISODE.firstNotNullOfOrNull { it.find(stem) }?.groupValues
        if (both != null) return EpisodeNumber(season = both[1].toInt(), episode = both[2].toInt())
        return EPISODE_ONLY.firstNotNullOfOrNull { it.find(stem) }
            ?.let { found -> EpisodeNumber(season = seasonFromFolders(path), episode = found.groupValues[1].toInt()) }
    }

    fun isVideo(path: String): Boolean = extension(path) in VIDEO_EXTENSIONS

    /** Образец («sample»), которым трекеры показывают качество, — не серия и не фильм. */
    fun isSample(path: String, sizeBytes: Long): Boolean =
        SAMPLE.containsMatchIn(path.substringAfterLast('/')) && sizeBytes < SAMPLE_MAX_BYTES

    private fun seasonFromFolders(path: String): Int? = path.split('/').dropLast(1).asReversed()
        .firstNotNullOfOrNull { folder -> SEASON_FOLDER.find(folder)?.groupValues?.get(1)?.toInt() }

    private fun fileStem(path: String): String {
        val name = path.substringAfterLast('/')
        val extension = extension(name)
        return if (extension.isEmpty()) name else name.dropLast(extension.length + 1)
    }

    private fun extension(path: String): String =
        path.substringAfterLast('/').substringAfterLast('.', missingDelimiterValue = "").lowercase()

    private val SEASON_AND_EPISODE = listOf(
        Regex("""(?<![A-Za-z])S(\d{1,2})[ ._-]?E(\d{1,3})(?!\d)""", RegexOption.IGNORE_CASE),
        Regex("""(?<!\d)(\d{1,2})[xх](\d{2,3})(?!\d)""", RegexOption.IGNORE_CASE),
    )

    private val EPISODE_ONLY = listOf(
        Regex("""(?:Серия|Эпизод|Episode|Ep)[ ._-]*(\d{1,3})(?!\d)""", RegexOption.IGNORE_CASE),
        Regex("""(?<![A-Za-z\d])E(\d{2,3})(?!\d)""", RegexOption.IGNORE_CASE),
        Regex("""(?<!\d)(\d{1,3})[ ._-]*(?:серия|series)""", RegexOption.IGNORE_CASE),
        Regex("""^(\d{1,3})(?:[ ._-]|$)"""),
        Regex("""[ ._-](\d{1,3})$"""),
    )

    private val SEASON_FOLDER =
        Regex("""(?:Season|Сезон|(?<![A-Za-z])S)[ ._-]?(\d{1,2})(?!\d)""", RegexOption.IGNORE_CASE)

    private val SAMPLE = Regex("""sample""", RegexOption.IGNORE_CASE)

    private val VIDEO_EXTENSIONS = setOf("mkv", "mp4", "avi", "m4v", "mov", "ts", "m2ts", "webm", "wmv", "mpg", "mpeg")

    private const val SAMPLE_MAX_BYTES = 300L * 1024 * 1024
}
