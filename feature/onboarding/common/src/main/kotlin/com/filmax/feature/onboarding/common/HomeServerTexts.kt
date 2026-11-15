package com.filmax.feature.onboarding.common

/** Заголовок выбора сервера: у приложения хаба — сам хаб, у основного — домашний Jellyfin. */
fun serverTitle(state: HomeServerState): String = if (state.hub) "Filmax Hub" else "Домашний сервер"

/** Что такое хаб — одной фразой на шаге выбора: телефону и ТВ она общая. */
const val HUB_HINT = "Хаб Filmax в этой сети: витрина фильмов и сериалов, а видео — из торрентов."
