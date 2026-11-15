package com.filmax.feature.torrents.common

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.filmax.core.domain.catalog.CatalogRepository
import com.filmax.core.domain.catalog.model.Item
import com.filmax.core.domain.catalog.model.VideoRef
import com.filmax.core.domain.common.RequestResult
import com.filmax.core.domain.source.ContentSource
import com.filmax.core.domain.source.ContentSourceRepository
import com.filmax.core.domain.torrent.TorrServerRepository
import com.filmax.core.domain.torrent.TorrentChoices
import com.filmax.core.domain.torrent.TorrentRelease
import com.filmax.core.domain.torrent.TorrentRepository
import com.filmax.core.domain.torrent.toChosen
import com.filmax.core.domain.torrent.torrentQuery
import com.filmax.core.presentation.BaseScreenModel
import com.filmax.core.presentation.input.erasedText
import com.filmax.core.presentation.input.typedRaw
import com.filmax.feature.torrents.common.navigation.TorrentsRoute
import kotlinx.coroutines.Job

/**
 * Выбор раздачи для тайтла, как в Lampa: TorrServer ищет её на трекерах через Jackett, экран
 * фильтрует по качеству, озвучке и сезону. Выбранная раздача запоминается на тайтл, и дальше
 * плеер берёт видео из неё, а прогресс пишется в трек источника.
 */
class TorrentsScreenModel(
    savedStateHandle: SavedStateHandle,
    private val catalog: CatalogRepository,
    private val servers: TorrServerRepository,
    private val torrents: TorrentRepository,
    private val choices: TorrentChoices,
    sources: ContentSourceRepository,
) : BaseScreenModel<TorrentsState, TorrentsSideEffect, TorrentsEvent>(
    TorrentsState(sourceName = sources.source.value.label),
) {

    private val route = savedStateHandle.toRoute<TorrentsRoute>()
    private val video: VideoRef? = VideoRef.of(route.season, route.number)
    private var item: Item? = null
    private var search: Job? = null

    init {
        onFetchData()
        observeChoice()
    }

    override fun onEvent(event: TorrentsEvent) {
        when (event) {
            is TorrentsEvent.Type -> edit { typedRaw(it, event.key, MAX_ADDRESS_LENGTH) }
            TorrentsEvent.Erase -> edit(::erasedText)
            is TorrentsEvent.SwitchLayout -> screenModelScope { updateState { it.copy(layout = event.layout) } }
            TorrentsEvent.Connect -> connect()
            TorrentsEvent.ChangeServer -> changeServer()
            TorrentsEvent.Retry -> if (item == null) onFetchData() else search()
            is TorrentsEvent.OpenFilter -> screenModelScope {
                updateState { it.copy(filter = event.kind, filterOpen = true) }
            }
            is TorrentsEvent.ChooseOption -> chooseOption(event.index)
            TorrentsEvent.CloseFilter -> screenModelScope { updateState { it.copy(filterOpen = false) } }
            is TorrentsEvent.Choose -> choose(event.release)
            // Без выбранной раздачи плеер берёт поток источника: забыть её и есть «играть с источника».
            TorrentsEvent.UseSource -> screenModelScope {
                choices.forget(route.itemId)
                postSideEffect(TorrentsSideEffect.Play(route.itemId, video))
            }
        }
    }

    /** Тайтл, затем сервер: без названия и года искать нечего, без сервера — негде. */
    override fun onFetchData() {
        screenModelScope {
            updateState { it.copy(stage = TorrentsStage.SEARCHING, error = null) }
            when (val result = catalog.getItemDetails(route.itemId)) {
                is RequestResult.Success -> {
                    item = result.data
                    updateState { it.copy(title = result.data.title, subtitle = subtitle(result.data, video)) }
                    observeServer()
                }
                is RequestResult.Error -> updateState { it.copy(stage = TorrentsStage.FAILED, error = ITEM_FAILED) }
            }
        }
    }

    /** Подключили сервер — сразу поиск; забыли — экран подключения. */
    private suspend fun observeServer() {
        servers.server.collect { server ->
            updateState { it.copy(server = server) }
            if (server == null) {
                search?.cancel()
                updateState { it.copy(stage = TorrentsStage.NO_SERVER, releases = emptyList(), error = null) }
            } else {
                search()
            }
        }
    }

    private fun observeChoice() {
        screenModelScope {
            choices.observe(route.itemId).collect { chosen -> updateState { it.copy(chosen = chosen) } }
        }
    }

    private fun search() {
        val item = item ?: return
        search?.cancel()
        search = screenModelScope {
            updateState { it.copy(stage = TorrentsStage.SEARCHING, error = null) }
            when (val result = torrents.search(item.torrentQuery())) {
                is RequestResult.Success -> updateState { current ->
                    current.copy(
                        stage = TorrentsStage.READY,
                        releases = result.data,
                        filters = current.filters.startingAt(video, result.data),
                    )
                }
                is RequestResult.Error -> updateState { it.copy(stage = TorrentsStage.FAILED, error = SEARCH_FAILED) }
            }
        }
    }

    private fun edit(change: (String) -> String) {
        screenModelScope { updateState { it.copy(address = change(it.address), error = null) } }
    }

    /**
     * Другой сервер — с адресом прежнего в поле: «Другой сервер», нажатый по ошибке, не стоит
     * набора адреса заново, и порт обычно меняют, а не IP.
     */
    private fun changeServer() {
        val previous = state.server?.address?.substringAfter("://").orEmpty()
        servers.forget()
        screenModelScope { updateState { it.copy(address = previous) } }
    }

    /** Адрес читается в корутине: так в него попадут и клавиши, нажатые перед «Подключить». */
    private fun connect() {
        if (state.connecting) return
        screenModelScope {
            val address = state.address
            val error = if (address.isBlank()) {
                NO_ADDRESS
            } else {
                updateState { it.copy(connecting = true, error = null) }
                // Удачное подключение дальше ведёт поток сервера: он и запустит поиск.
                val failed = servers.connect(address) is RequestResult.Error
                if (failed) "TorrServer не ответил по адресу $address. $CHECK_SERVER" else null
            }
            updateState { it.copy(connecting = false, error = error) }
        }
    }

    private fun chooseOption(index: Int) {
        screenModelScope {
            updateState { it.copy(filters = it.filtersWith(it.filter, index), filterOpen = false) }
        }
    }

    /**
     * Раздачу сначала открывает TorrServer: у магнита список файлов появится, только когда
     * найдутся пиры. Выбор запоминается после того, как видео в раздаче нашлось, — иначе тайтл
     * остался бы привязан к раздаче, которую не сыграть.
     */
    private fun choose(release: TorrentRelease) {
        val item = item ?: return
        if (state.opening != null) return
        screenModelScope {
            updateState { it.copy(opening = release.link, error = null) }
            val result = torrents.open(release.link, poster = item.posters.medium.ifBlank { null })
            val failure = when {
                result is RequestResult.Error -> "TorrServer не открыл раздачу. $CHECK_SERVER"
                result is RequestResult.Success && result.data.videos.isEmpty() -> NO_VIDEO
                else -> null
            }
            if (failure == null) choices.choose(route.itemId, release.toChosen())
            updateState { it.copy(opening = null, error = failure) }
            if (failure == null) postSideEffect(TorrentsSideEffect.Play(route.itemId, video))
        }
    }
}

/** Год и серия, которая запустится из раздачи: «2024 · запустится S2E5». */
private fun subtitle(item: Item, video: VideoRef?): String {
    val episode = (video as? VideoRef.Episode)?.let { "запустится S${it.season}E${it.number}" }
    return listOfNotNull(item.year.takeIf { it > 0 }?.toString(), episode).joinToString(" · ")
}

/**
 * Откуда тайтл играет без торрента — для кнопки «Играть с …». У хаба своих видео нет: всё играет
 * из раздач, и возвращаться некуда.
 */
private val ContentSource.label: String?
    get() = when (this) {
        ContentSource.KINOPUB -> "kino.pub"
        ContentSource.JELLYFIN -> "домашнего сервера"
        ContentSource.HUB -> null
    }

/** Адрес с портом и схемой — с запасом: «http://192.168.100.200:8090». */
private const val MAX_ADDRESS_LENGTH = 64

private const val CHECK_SERVER = "Проверьте, что TorrServer запущен и в той же сети."
private const val NO_ADDRESS = "Наберите адрес TorrServer, например 192.168.1.20:8090."
private const val NO_VIDEO = "В раздаче нет видео — выберите другую."
private const val ITEM_FAILED = "Не удалось загрузить тайтл."
private const val SEARCH_FAILED = "Поиск не ответил. Проверьте, что в TorrServer настроен Jackett."
