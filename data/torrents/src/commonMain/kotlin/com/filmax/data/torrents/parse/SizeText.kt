package com.filmax.data.torrents.parse

/**
 * Размер раздачи из подписи TorrServer: «40.0 GiB», «591.1 MiB». Torznab TorrServer отдаёт размер
 * уже отформатированным, байтов в ответе нет. Дробь бывает и через запятую, единицы — и по-русски.
 */
internal object SizeText {

    fun bytes(text: String?): Long? {
        val found = text?.let { SIZE.find(it.trim()) } ?: return null
        val number = found.groupValues[1].replace(',', '.').toDoubleOrNull()
        val unit = UNITS[found.groupValues[2].lowercase()]
        return if (number != null && unit != null) (number * unit).toLong() else null
    }

    private val SIZE = Regex("""^(\d+(?:[.,]\d+)?)\s*([KMGTКМГТ]?i?[BБ]|bytes?|байт)""", RegexOption.IGNORE_CASE)

    private const val KILO = 1024.0

    private val UNITS: Map<String, Double> = buildMap {
        listOf("b", "б", "byte", "bytes", "байт").forEach { put(it, 1.0) }
        listOf("kb", "kib", "кб").forEach { put(it, KILO) }
        listOf("mb", "mib", "мб").forEach { put(it, KILO * KILO) }
        listOf("gb", "gib", "гб").forEach { put(it, KILO * KILO * KILO) }
        listOf("tb", "tib", "тб").forEach { put(it, KILO * KILO * KILO * KILO) }
    }
}
