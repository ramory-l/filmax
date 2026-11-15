package com.filmax.data.source

import com.filmax.core.domain.source.ContentSource
import com.filmax.core.domain.source.ContentSourceRepository
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Источник — настройка устройства. Незнакомое, пустое или недоступное в этой сборке значение —
 * первый из [available]: у основного приложения это kino.pub, как до выбора.
 */
internal class ContentSourceRepositoryImpl(
    private val settings: Settings,
    override val available: List<ContentSource> = MAIN_SOURCES,
) : ContentSourceRepository {

    init {
        require(available.isNotEmpty()) { "Сборке нужен хотя бы один источник" }
    }

    private val state = MutableStateFlow(stored())

    override val source: StateFlow<ContentSource> = state.asStateFlow()

    override suspend fun select(source: ContentSource) {
        require(source in available) { "Источник $source недоступен в этой сборке" }
        settings.putString(KEY, source.name)
        state.value = source
    }

    private fun stored(): ContentSource =
        settings.getStringOrNull(KEY)?.let { name -> available.firstOrNull { it.name == name } }
            ?: available.first()

    private companion object {
        const val KEY = "content_source"
    }
}

/** Источники основного приложения: хаб — у отдельного приложения «Плёнка». */
internal val MAIN_SOURCES = listOf(ContentSource.KINOPUB, ContentSource.JELLYFIN)
