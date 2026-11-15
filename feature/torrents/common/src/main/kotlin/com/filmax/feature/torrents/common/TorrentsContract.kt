package com.filmax.feature.torrents.common

import com.filmax.core.domain.catalog.model.VideoRef
import com.filmax.core.domain.torrent.ChosenRelease
import com.filmax.core.domain.torrent.TorrServer
import com.filmax.core.domain.torrent.TorrentRelease
import com.filmax.core.presentation.input.KeyboardLayout

/** Что на экране: без сервера — подключение, дальше — поиск, раздачи или сбой. */
enum class TorrentsStage { NO_SERVER, SEARCHING, READY, FAILED }

data class TorrentsState(
    val stage: TorrentsStage = TorrentsStage.SEARCHING,
    val title: String = "",
    /** Год и что запустится из раздачи: «2024 · серия S2E5». */
    val subtitle: String = "",
    /** Откуда видео без торрента — подпись кнопки возврата: «kino.pub», «домашнего сервера». */
    val sourceName: String = "",
    val server: TorrServer? = null,
    /** Набранный адрес TorrServer. */
    val address: String = "",
    /** Адрес — цифры и точки, поэтому клавиатура открывается цифровой. */
    val layout: KeyboardLayout = KeyboardLayout.DIGITS,
    val connecting: Boolean = false,
    /** Раздачи тайтла, уже отобранные по названию и году, — до фильтров экрана. */
    val releases: List<TorrentRelease> = emptyList(),
    val filters: ReleaseFilters = ReleaseFilters(),
    /**
     * Фильтр последней открытой панели вариантов. Остаётся и после закрытия: на его чип панель
     * возвращает фокус.
     */
    val filter: ReleaseFilterKind = ReleaseFilterKind.QUALITY,
    val filterOpen: Boolean = false,
    /** Раздача, из которой тайтл играет сейчас; null — играет источник. */
    val chosen: ChosenRelease? = null,
    /** Ссылка раздачи, которую открывает TorrServer: её строка показывает ожидание. */
    val opening: String? = null,
    val error: String? = null,
) {
    /** Варианты фильтров — из того, что нашлось: кнопки «4K» без 4K-раздач не бывает. */
    val options: ReleaseOptions by lazy { ReleaseOptions.of(releases) }

    /** Раздачи под фильтрами и в выбранном порядке — то, что показывает список. */
    val shown: List<TorrentRelease> by lazy { filters.applyTo(releases) }
}

sealed interface TorrentsEvent {
    /** Клавиша экранной клавиатуры ТВ: символ адреса. */
    data class Type(val key: String) : TorrentsEvent
    data object Erase : TorrentsEvent
    data class SwitchLayout(val layout: KeyboardLayout) : TorrentsEvent

    data object Connect : TorrentsEvent

    /** Забыть TorrServer и ввести другой адрес. */
    data object ChangeServer : TorrentsEvent

    /** Повторить то, что не удалось: загрузку тайтла или поиск. */
    data object Retry : TorrentsEvent

    data class OpenFilter(val kind: ReleaseFilterKind) : TorrentsEvent

    /** Вариант [index] в панели открытого фильтра — в порядке [TorrentsState.optionLabels]. */
    data class ChooseOption(val index: Int) : TorrentsEvent
    data object CloseFilter : TorrentsEvent

    data class Choose(val release: TorrentRelease) : TorrentsEvent {
        /**
         * В телеметрию событие уходит строкой — только названием раздачи: ссылка Jackett несёт его
         * ключ API, а разбор названия в крошке не нужен.
         */
        override fun toString(): String = "Choose(${release.title})"
    }

    /** Играть снова из источника: выбор раздачи забывается, и экран сменяется плеером. */
    data object UseSource : TorrentsEvent
}

sealed interface TorrentsSideEffect {
    /** Раздача выбрана и открыта или источник возвращён: экран сменяется плеером на том же видео. */
    data class Play(val itemId: Int, val video: VideoRef?) : TorrentsSideEffect
}
