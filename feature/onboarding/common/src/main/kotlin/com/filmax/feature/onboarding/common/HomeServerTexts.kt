package com.filmax.feature.onboarding.common

/** Заголовок выбора сервера: у «Плёнки» — домашний хаб, у Filmax — домашний Jellyfin. */
fun serverTitle(state: HomeServerState): String = if (state.hub) "Домашний хаб" else "Домашний сервер"

/** Что такое хаб — одной фразой на шаге выбора: телефону и ТВ она общая. */
const val HUB_HINT = "Хаб на компьютере в этой сети: витрина фильмов и сериалов, а видео — из торрентов."
