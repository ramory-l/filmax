package com.filmax.feature.player.common

import com.filmax.core.domain.catalog.model.SubtitleTrack
import com.filmax.core.domain.catalog.model.subtitleLanguageName
import com.filmax.core.domain.playback.PlaybackSettings

/**
 * Субтитры трека для меню: «Выкл» первым, дальше языки трека — каждый по одному разу. Подпись —
 * ключ ленивого списка вкладки, и у двух треков одного языка (два `spa`, `rus` и `ru`) она
 * совпадала: Compose падал с «Key "spa" was already used». Отдельный вариант на каждый трек
 * ничего бы не дал — выбор идёт предпочитаемым языком Media3, и такие треки им неотличимы.
 */
internal fun subtitleOptions(subtitles: List<SubtitleTrack>): List<SubtitleOption> {
    val languages = subtitles.map { SubtitleOption(subtitleLanguageName(it.lang), it.lang) }
    return (listOf(SubtitleOption(PlaybackSettings.SubtitleOff, null)) + languages).distinctBy { it.label }
}

/** Вариант по языку из настроек; «Выкл» или языка у трека нет — первый, то есть «Выкл». */
internal fun List<SubtitleOption>.preferred(language: String): SubtitleOption =
    firstOrNull { language != PlaybackSettings.SubtitleOff && it.label == language } ?: first()
