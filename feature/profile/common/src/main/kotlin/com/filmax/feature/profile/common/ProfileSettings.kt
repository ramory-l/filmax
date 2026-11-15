package com.filmax.feature.profile.common

import com.filmax.core.domain.playback.PlaybackSettingsRepository
import com.filmax.core.domain.settings.ThemeSettingsRepository
import com.filmax.core.domain.source.ContentSourceRepository
import com.filmax.core.domain.source.ExtraSources

/**
 * Настройки устройства, которые меняет Профиль: воспроизведение, оформление, источник контента и
 * переключатель домашнего сервера и раздач. Одним объектом — иначе конструктор
 * [ProfileScreenModel] за порогом detekt.
 */
class ProfileSettings(
    val playback: PlaybackSettingsRepository,
    val theme: ThemeSettingsRepository,
    val sources: ContentSourceRepository,
    val extras: ExtraSources,
)
