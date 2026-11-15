package com.filmax.app

import com.filmax.data.hub.di.hubLoginModule
import com.filmax.data.source.di.hubOnlySourceModule
import org.koin.core.module.Module

/**
 * Application «Плёнки»: граф тот же, что у Filmax, но источник один — домашний хаб, а шаг
 * «Домашний сервер» входит на него, а не в Jellyfin. Модули ложатся ПОВЕРХ общих: Koin перекрывает
 * определение тем модулем, что загружен позже, и экранам подменять ничего не нужно.
 */
class PlenkaApplication : FilmaxApplication() {
    override fun dataModules(): List<Module> = super.dataModules() + hubOnlySourceModule + hubLoginModule
}
