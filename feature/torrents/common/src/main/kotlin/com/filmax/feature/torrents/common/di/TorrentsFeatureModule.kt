package com.filmax.feature.torrents.common.di

import com.filmax.feature.torrents.common.TorrentsScreenModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val torrentsFeatureModule = module {
    viewModelOf(::TorrentsScreenModel)
}
