package com.filmax.core.domain.source

import kotlinx.coroutines.flow.StateFlow

/**
 * Дополнительные способы смотреть в основном приложении: домашний сервер (Jellyfin) и раздачи
 * через TorrServer. До уровня kino.pub они не доведены, поэтому по умолчанию спрятаны: вход на
 * домашний сервер, выбор источника, кнопка раздач в деталях и подмена потока раздачей работают,
 * только когда переключатель в Профиле включён.
 *
 * У «Плёнки» раздачи — единственный способ смотреть: там они включены всегда, и переключателя нет.
 */
interface ExtraSources {

    val enabled: StateFlow<Boolean>

    /** Переключатель есть в Профиле; false — сборка решила за пользователя. */
    val switchable: Boolean

    suspend fun setEnabled(enabled: Boolean)
}
