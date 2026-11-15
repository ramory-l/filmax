package com.filmax.data.source.di

import com.filmax.core.domain.auth.AuthRepository
import com.filmax.core.domain.catalog.CatalogRepository
import com.filmax.core.domain.playback.StreamRepository
import com.filmax.core.domain.search.SearchRepository
import com.filmax.core.domain.source.ContentSource
import com.filmax.core.domain.source.ContentSourceRepository
import com.filmax.core.domain.source.ExtraSources
import com.filmax.core.domain.user.UserRepository
import com.filmax.core.domain.watching.WatchingRepository
import com.filmax.core.domain.watchlist.WatchlistRepository
import com.filmax.data.source.AlwaysOnExtraSources
import com.filmax.data.source.ContentSourceRepositoryImpl
import com.filmax.data.source.ExtraSourcesImpl
import com.filmax.data.source.RoutingAuthRepository
import com.filmax.data.source.RoutingCatalogRepository
import com.filmax.data.source.RoutingSearchRepository
import com.filmax.data.source.RoutingStreamRepository
import com.filmax.data.source.RoutingUserRepository
import com.filmax.data.source.RoutingWatchingRepository
import com.filmax.data.source.RoutingWatchlistRepository
import com.filmax.data.source.SourceSwitch
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.module

/**
 * Контракты, которые экраны берут без квалификатора, — переключатели между kino.pub, домашним
 * сервером и хабом. Грузится ПОСЛЕ data-модулей источников: занимает их ключи без квалификатора,
 * а реализации берёт по квалификатору [ContentSource].
 */
val sourceModule = module {
    single<ContentSourceRepository> { ContentSourceRepositoryImpl(settings = get()) }
    single<AuthRepository> { RoutingAuthRepository(switch()) }
    single<CatalogRepository> { RoutingCatalogRepository(switch()) }
    single<SearchRepository> { RoutingSearchRepository(switch()) }
    single<UserRepository> { RoutingUserRepository(switch()) }
    single<WatchingRepository> { RoutingWatchingRepository(switch()) }
    single<WatchlistRepository> { RoutingWatchlistRepository(switch()) }
    single<ExtraSources> { ExtraSourcesImpl(settings = get(), sources = get()) }
    single<StreamRepository> { RoutingStreamRepository(switch(), torrents = get(), extras = get()) }
}

/**
 * Выбор источника приложения «Плёнка»: только хаб, без kino.pub и домашнего сервера, а раздачи
 * включены всегда.
 * Грузится после [sourceModule] и перекрывает его выбор источника — остальные переключатели те же.
 */
val hubOnlySourceModule = module {
    single<ContentSourceRepository> {
        ContentSourceRepositoryImpl(settings = get(), available = listOf(ContentSource.HUB))
    }
    single<ExtraSources> { AlwaysOnExtraSources }
}

/**
 * Реализации источников берутся лениво: в основном приложении модуль хаба есть, но источник
 * недоступен — его реализации так и не создаются, и наоборот.
 */
private inline fun <reified T : Any> Scope.switch(): SourceSwitch<T> = SourceSwitch(
    sources = get(),
    kinopub = inject(named(ContentSource.KINOPUB)),
    homeServer = inject(named(ContentSource.JELLYFIN)),
    hub = inject(named(ContentSource.HUB)),
)
