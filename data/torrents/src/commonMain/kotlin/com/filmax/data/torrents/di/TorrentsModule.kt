package com.filmax.data.torrents.di

import com.filmax.core.domain.torrent.TorrServerRepository
import com.filmax.core.domain.torrent.TorrentChoices
import com.filmax.core.domain.torrent.TorrentRepository
import com.filmax.core.domain.torrent.TorrentStreams
import com.filmax.data.torrents.TorrServerRepositoryImpl
import com.filmax.data.torrents.TorrServerStore
import com.filmax.data.torrents.TorrentChoicesImpl
import com.filmax.data.torrents.TorrentRepositoryImpl
import com.filmax.data.torrents.TorrentStreamsImpl
import com.filmax.data.torrents.remote.TorrServerApi
import com.filmax.data.torrents.remote.torrServerHttpClient
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Свой HTTP-клиент TorrServer — под квалификатором, чтобы не занять ключ клиента kino.pub. */
private val TorrServerHttp = named("torrserver_http")

/**
 * Торренты — не источник каталога, а второй способ посмотреть тайтл любого источника: поэтому
 * без квалификатора [com.filmax.core.domain.source.ContentSource]. Грузится до data:source:
 * переключатель потоков спрашивает [TorrentStreams], прежде чем отдать трек источнику.
 */
val torrentsModule = module {
    single { TorrServerStore(settings = get()) }
    single(TorrServerHttp) { torrServerHttpClient(engine = get()) }
    single { TorrServerApi(client = get(TorrServerHttp)) }
    single<TorrServerRepository> { TorrServerRepositoryImpl(api = get(), store = get()) }
    single<TorrentRepository> { TorrentRepositoryImpl(api = get(), store = get()) }
    single<TorrentChoices> { TorrentChoicesImpl(settings = get()) }
    single<TorrentStreams> { TorrentStreamsImpl(choices = get(), torrents = get(), store = get()) }
}
