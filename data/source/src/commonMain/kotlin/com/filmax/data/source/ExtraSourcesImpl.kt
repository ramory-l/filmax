package com.filmax.data.source

import com.filmax.core.domain.source.ContentSource
import com.filmax.core.domain.source.ContentSourceRepository
import com.filmax.core.domain.source.ExtraSources
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Переключатель основного приложения — настройка устройства. Пока его не трогали, он выключен,
 * кроме одного случая: источник уже не kino.pub. Значит, домашним сервером пользовались до
 * обновления, и прятать его вместе с выбором источника нельзя — вернуться было бы неоткуда.
 */
internal class ExtraSourcesImpl(
    private val settings: Settings,
    private val sources: ContentSourceRepository,
) : ExtraSources {

    private val state = MutableStateFlow(
        settings.getBooleanOrNull(KEY) ?: (sources.source.value != ContentSource.KINOPUB),
    )

    override val enabled: StateFlow<Boolean> = state.asStateFlow()

    override val switchable: Boolean = true

    override suspend fun setEnabled(enabled: Boolean) {
        settings.putBoolean(KEY, enabled)
        state.value = enabled
        // Выключили, сидя на домашнем сервере, — назад на kino.pub: строки выбора источника больше
        // нет, и иначе сменить его было бы негде. Граф сам уведёт к корню или ко входу.
        if (!enabled && sources.source.value != ContentSource.KINOPUB) sources.select(ContentSource.KINOPUB)
    }

    private companion object {
        const val KEY = "extra_sources"
    }
}

/** «Плёнка»: раздачи — единственный способ смотреть, выключить их нечем и незачем. */
internal object AlwaysOnExtraSources : ExtraSources {

    override val enabled: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()

    override val switchable: Boolean = false

    override suspend fun setEnabled(enabled: Boolean) = Unit
}
