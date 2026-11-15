package com.filmax.data.hub.di

import com.filmax.core.domain.auth.AuthRepository
import com.filmax.core.domain.catalog.CatalogRepository
import com.filmax.core.domain.playback.StreamRepository
import com.filmax.core.domain.search.SearchRepository
import com.filmax.core.domain.source.ContentSource
import com.filmax.core.domain.source.HomeServerRepository
import com.filmax.core.domain.user.UserRepository
import com.filmax.core.domain.watching.WatchingRepository
import com.filmax.core.domain.watchlist.WatchlistRepository
import com.filmax.core.network.di.DeviceCache
import com.filmax.data.hub.HubAuthRepository
import com.filmax.data.hub.HubCatalogRepository
import com.filmax.data.hub.HubDiscovery
import com.filmax.data.hub.HubSearchRepository
import com.filmax.data.hub.HubServerRepository
import com.filmax.data.hub.HubSession
import com.filmax.data.hub.HubStreamRepository
import com.filmax.data.hub.HubTitles
import com.filmax.data.hub.HubUserRepository
import com.filmax.data.hub.HubWatchingRepository
import com.filmax.data.hub.HubWatchlistRepository
import com.filmax.data.hub.local.HubProgress
import com.filmax.data.hub.mapper.HubMapper
import com.filmax.data.hub.remote.HubApi
import com.filmax.data.hub.remote.hubHttpClient
import io.ktor.util.date.getTimeMillis
import kotlinx.coroutines.Dispatchers
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Свой HTTP-клиент хаба — под квалификатором, чтобы не занять ключ клиента kino.pub. */
private val HubHttp = named("hub_http")

/**
 * Filmax Hub. Репозитории контрактов — под квалификатором [ContentSource.HUB]: экраны получают
 * их через переключатель источника (data:source). Модуль есть и в основном приложении, но там хаб
 * недоступен, и ленивые реализации не создаются. Прогресс и «Буду смотреть» — в кеше устройства:
 * их много, а секретов в них нет.
 *
 * Диспетчер поиска в сети берётся здесь: DI-модуль — единственное место, где его берут напрямую.
 */
// Единственная точка прямого Dispatchers в модуле (см. KDoc), правило исключений не делает.
@Suppress("InjectDispatcher")
val hubModule = module {
    single { HubSession(settings = get()) }
    single(HubHttp) { hubHttpClient(engine = get()) }
    single { HubApi(client = get(HubHttp), session = get()) }
    single { HubProgress(settings = get(DeviceCache), clock = ::getTimeMillis) }
    single { HubWatchlistRepository(settings = get(DeviceCache)) }
    single { HubMapper(session = get(), progress = get(), inWatchlist = get<HubWatchlistRepository>()::has) }
    single { HubTitles(api = get(), mapper = get()) }
    single { HubDiscovery(dispatcher = Dispatchers.Default) }

    val hub = named(ContentSource.HUB)
    single<HomeServerRepository>(hub) {
        HubServerRepository(api = get(), session = get(), discovery = get(), torrServers = get())
    }
    single<AuthRepository>(hub) { HubAuthRepository(session = get()) }
    single<CatalogRepository>(hub) { HubCatalogRepository(api = get(), titles = get(), mapper = get()) }
    single<SearchRepository>(hub) { HubSearchRepository(api = get(), mapper = get()) }
    single<UserRepository>(hub) { HubUserRepository(session = get()) }
    single<WatchingRepository>(hub) { HubWatchingRepository(progress = get(), titles = get()) }
    single<WatchlistRepository>(hub) { get<HubWatchlistRepository>() }
    single<StreamRepository>(hub) { HubStreamRepository(titles = get(), autoChoice = get(), torrents = get()) }
}

/**
 * Приложение «Плёнка»: шаг «Домашний сервер» входит на хаб, а не в Jellyfin. Грузится
 * после модуля Jellyfin и перекрывает его вход без квалификатора.
 */
val hubLoginModule = module {
    single<HomeServerRepository> { get(named(ContentSource.HUB)) }
}
