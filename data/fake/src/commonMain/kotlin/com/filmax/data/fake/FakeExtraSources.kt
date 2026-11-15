package com.filmax.data.fake

import com.filmax.core.domain.source.ExtraSources
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Переключатель домашнего сервера и раздач в памяти — для юнит-тестов ScreenModel'ей. По умолчанию
 * включён: тесты экранов проверяют всё, что экран умеет показать.
 */
class FakeExtraSources(
    enabled: Boolean = true,
    override val switchable: Boolean = true,
) : ExtraSources {

    private val state = MutableStateFlow(enabled)

    override val enabled: StateFlow<Boolean> = state.asStateFlow()

    override suspend fun setEnabled(enabled: Boolean) {
        state.value = enabled
    }
}
