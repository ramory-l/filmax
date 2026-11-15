package com.filmax.app

import com.filmax.data.hub.di.hubLoginModule
import com.filmax.data.source.di.hubOnlySourceModule
import org.koin.core.module.Module

/**
 * Application сборки «Filmax Торренты»: граф тот же, что у основного приложения, но источник один —
 * хаб, а шаг «Домашний сервер» входит на него, а не в Jellyfin. Модули ложатся ПОВЕРХ общих: Koin
 * перекрывает определение тем модулем, что загружен позже, и экранам подменять ничего не нужно.
 */
class TorFilmaxApplication : FilmaxApplication() {
    override fun dataModules(): List<Module> = super.dataModules() + hubOnlySourceModule + hubLoginModule
}
