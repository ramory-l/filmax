package com.filmax.feature.details.common.di

import com.filmax.feature.details.common.DetailsPlayback
import com.filmax.feature.details.common.DetailsScreenModel
import com.filmax.feature.details.common.FolderPickerScreenModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val detailsModule = module {
    factoryOf(::DetailsPlayback)
    viewModelOf(::DetailsScreenModel)
    viewModelOf(::FolderPickerScreenModel)
}
