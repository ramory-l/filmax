package com.filmax.data.hub

import com.filmax.core.domain.auth.AuthRepository
import com.filmax.core.domain.auth.model.DeviceCode
import com.filmax.core.domain.auth.model.Token
import com.filmax.core.domain.catalog.model.Item
import com.filmax.core.domain.catalog.model.ItemPage
import com.filmax.core.domain.catalog.model.ItemType
import com.filmax.core.domain.catalog.model.Pagination
import com.filmax.core.domain.common.RequestResult
import com.filmax.core.domain.common.safeRequest
import com.filmax.core.domain.search.SearchRepository
import com.filmax.core.domain.user.UserRepository
import com.filmax.core.domain.user.model.BookmarkFolder
import com.filmax.core.domain.user.model.UserProfile
import com.filmax.data.hub.mapper.HubMapper
import com.filmax.data.hub.remote.HubApi
import kotlinx.coroutines.flow.Flow

/**
 * Вход в хаб — подключение по адресу (его делает [HubServerRepository]); кода устройства и
 * токенов нет. Выход — забыть хаб: граф уводит на шаг подключения.
 */
internal class HubAuthRepository(private val session: HubSession) : AuthRepository {

    override val isAuthenticated: Flow<Boolean> = session.signedIn

    override suspend fun requestDeviceCode(): RequestResult<DeviceCode> = RequestResult.Error(NO_CODE)

    override suspend fun pollForToken(code: String, username: String, timestamp: Long): RequestResult<Token> =
        RequestResult.Error(NO_CODE)

    override suspend fun refreshToken(refreshToken: String): RequestResult<Token> = RequestResult.Error(NO_CODE)

    override suspend fun logout(): RequestResult<Unit> {
        session.clear()
        return RequestResult.Success(Unit)
    }

    private companion object {
        const val NO_CODE = "У хаба нет входа по коду: подключитесь по адресу"
    }
}

/** Поиск хаба — по названиям в TMDB; по актёрам и режиссёрам протокол пока не ищет. */
internal class HubSearchRepository(private val api: HubApi, private val mapper: HubMapper) : SearchRepository {

    override suspend fun search(query: String, type: ItemType?, perPage: Int): RequestResult<List<Item>> = safeRequest {
        api.search(query).items.map(mapper::item).filter { type == null || it.type == type }.take(perPage)
    }

    override suspend fun searchByActor(actor: String, perPage: Int): RequestResult<List<Item>> =
        RequestResult.Success(emptyList())

    override suspend fun searchByDirector(director: String, perPage: Int): RequestResult<List<Item>> =
        RequestResult.Success(emptyList())
}

/**
 * «Аккаунт» хаба — сам хаб: его имя в Профиле, без подписки и папок (их не обещают и возможности
 * источника, экраны папки не показывают).
 */
internal class HubUserRepository(private val session: HubSession) : UserRepository {

    override suspend fun getProfile(): RequestResult<UserProfile> = safeRequest {
        UserProfile(id = 0, username = session.require().name, email = null, avatarUrl = null, subscription = null)
    }

    // Устройства хаб не учитывает: подписывать нечего.
    override suspend fun registerDevice(title: String, hardware: String, software: String): RequestResult<Unit> =
        RequestResult.Success(Unit)

    override suspend fun getBookmarkFolders(): RequestResult<List<BookmarkFolder>> = RequestResult.Success(emptyList())

    override suspend fun getBookmarkItems(folderId: Int, page: Int): RequestResult<ItemPage> =
        RequestResult.Success(ItemPage(emptyList(), Pagination(total = 0, current = page, perPage = 0)))

    override suspend fun createBookmarkFolder(title: String): RequestResult<BookmarkFolder> =
        RequestResult.Error(NO_FOLDERS)

    override suspend fun deleteBookmarkFolder(folderId: Int): RequestResult<Unit> = RequestResult.Error(NO_FOLDERS)

    override suspend fun addToBookmark(itemId: Int, folderId: Int): RequestResult<Unit> =
        RequestResult.Error(NO_FOLDERS)

    override suspend fun removeFromBookmark(itemId: Int, folderId: Int): RequestResult<Unit> =
        RequestResult.Error(NO_FOLDERS)

    private companion object {
        const val NO_FOLDERS = "У хаба нет папок"
    }
}
