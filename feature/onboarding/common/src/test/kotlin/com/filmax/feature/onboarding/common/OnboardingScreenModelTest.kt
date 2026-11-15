package com.filmax.feature.onboarding.common

import com.filmax.core.domain.source.ContentSource
import com.filmax.data.fake.FakeAuthRepository
import com.filmax.data.fake.FakeBackend
import com.filmax.data.fake.FakeContentSourceRepository
import com.filmax.data.fake.FakeLatency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * С какого шага начинается вход. В основном приложении домашний сервер — альтернатива kino.pub, с
 * него можно вернуться; у «Filmax Торренты» хаб — единственный источник, и шаг подключения — весь вход.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingScreenModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val auth = FakeAuthRepository(FakeBackend(latency = FakeLatency.None, authenticated = false))

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `main app starts with kino pub and can come back from the home server`() = runTest(dispatcher) {
        val sources = FakeContentSourceRepository(ContentSource.JELLYFIN)
        val model = OnboardingScreenModel(auth, sources)
        assertTrue(model.states.value.homeServer)
        assertTrue(model.states.value.canLeaveHomeServer)

        model.dispatch(OnboardingEvent.CloseHomeServer)
        advanceUntilIdle()

        assertFalse(model.states.value.homeServer)
        assertEquals(ContentSource.KINOPUB, sources.source.value)
    }

    @Test
    fun `torrent app opens straight on the hub and stays there`() = runTest(dispatcher) {
        val sources = FakeContentSourceRepository(ContentSource.HUB, available = listOf(ContentSource.HUB))
        val model = OnboardingScreenModel(auth, sources)
        assertTrue(model.states.value.homeServer)
        assertFalse(model.states.value.canLeaveHomeServer)

        model.dispatch(OnboardingEvent.CloseHomeServer)
        advanceUntilIdle()

        assertTrue(model.states.value.homeServer)
        assertEquals(ContentSource.HUB, sources.source.value)
    }
}
