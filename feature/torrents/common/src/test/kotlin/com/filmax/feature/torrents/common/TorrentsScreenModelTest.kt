package com.filmax.feature.torrents.common

import androidx.lifecycle.SavedStateHandle
import com.filmax.core.domain.catalog.model.VideoRef
import com.filmax.core.domain.torrent.VideoQuality
import com.filmax.data.fake.FakeBackend
import com.filmax.data.fake.FakeCatalogRepository
import com.filmax.data.fake.FakeContentSourceRepository
import com.filmax.data.fake.FakeIds
import com.filmax.data.fake.FakeLatency
import com.filmax.data.fake.FakeTorrServerRepository
import com.filmax.data.fake.FakeTorrentChoices
import com.filmax.data.fake.FakeTorrentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Выбор раздачи на фейках: подключение TorrServer, поиск по тайтлу, фильтры и выбор, который
 * запоминается и запускает плеер. Robolectric — ради маршрута: `toRoute` читает Bundle.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TorrentsScreenModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val backend = FakeBackend(latency = FakeLatency.None)
    private val servers = FakeTorrServerRepository(initial = FakeTorrServerRepository.SERVER)
    private val torrents = FakeTorrentRepository()
    private val choices = FakeTorrentChoices()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `without a server the screen asks for its address and searches once connected`() = runTest(dispatcher) {
        servers.forget()
        val model = loaded(FakeIds.MEDIUM_SERIES)
        assertEquals(TorrentsStage.NO_SERVER, model.states.value.stage)

        FakeTorrServerRepository.SERVER.address.forEach { model.dispatch(TorrentsEvent.Type(it.toString())) }
        model.dispatch(TorrentsEvent.Connect)
        advanceUntilIdle()

        val state = model.states.value
        assertEquals(TorrentsStage.READY, state.stage)
        assertEquals(listOf("Северный ветер"), torrents.queries.single().names)
        assertTrue(torrents.queries.single().episodic)
    }

    @Test
    fun `unreachable address keeps the connect step with an explanation`() = runTest(dispatcher) {
        servers.forget()
        val model = loaded(FakeIds.MEDIUM_SERIES)

        "10.0.0.1".forEach { model.dispatch(TorrentsEvent.Type(it.toString())) }
        model.dispatch(TorrentsEvent.Connect)
        advanceUntilIdle()

        val state = model.states.value
        assertEquals(TorrentsStage.NO_SERVER, state.stage)
        assertTrue(assertNotNull(state.error).contains("10.0.0.1"))
        assertTrue(torrents.queries.isEmpty())
    }

    @Test
    fun `episode of the second season filters releases to that season by seeders`() = runTest(dispatcher) {
        val state = loaded(FakeIds.MEDIUM_SERIES, season = 2, number = 5).states.value

        assertEquals(2, state.filters.season)
        assertEquals(listOf("fhd", "s2"), state.shown.map { it.link })
        assertTrue(state.subtitle.endsWith(" · запустится S2E5"), state.subtitle)
    }

    @Test
    fun `quality panel offers only found qualities and filters by the chosen one`() = runTest(dispatcher) {
        val model = loaded(FakeIds.MEDIUM_SERIES)

        model.dispatch(TorrentsEvent.OpenFilter(ReleaseFilterKind.QUALITY))
        advanceUntilIdle()
        val qualities = model.states.value.optionLabels(ReleaseFilterKind.QUALITY)
        assertEquals(listOf("Любое качество", "4K", "1080p"), qualities)

        model.dispatch(TorrentsEvent.ChooseOption(1))
        advanceUntilIdle()
        val state = model.states.value
        assertFalse(state.filterOpen)
        assertEquals(VideoQuality.ULTRA_HD, state.filters.quality)
        assertEquals(listOf("uhd"), state.shown.map { it.link })
        assertEquals("4K", state.filterLabel(ReleaseFilterKind.QUALITY))
    }

    @Test
    fun `chosen release is remembered and starts the same episode`() = runTest(dispatcher) {
        val model = loaded(FakeIds.MEDIUM_SERIES, season = 2, number = 5)
        val effects = mutableListOf<TorrentsSideEffect>()
        model.observeSideEffects { effects += it }

        model.dispatch(TorrentsEvent.Choose(model.states.value.shown.first()))
        advanceUntilIdle()

        assertEquals(listOf("fhd"), torrents.opened)
        assertEquals("fhd", choices.chosen(FakeIds.MEDIUM_SERIES)?.link)
        assertEquals("fhd", model.states.value.chosen?.link)
        val play = TorrentsSideEffect.Play(FakeIds.MEDIUM_SERIES, VideoRef.Episode(season = 2, number = 5))
        assertEquals(listOf<TorrentsSideEffect>(play), effects)
    }

    @Test
    fun `release without video is not chosen`() = runTest(dispatcher) {
        torrents.videos = 0
        val model = loaded(FakeIds.HERO_MOVIE)
        val effects = mutableListOf<TorrentsSideEffect>()
        model.observeSideEffects { effects += it }

        model.dispatch(TorrentsEvent.Choose(model.states.value.shown.first()))
        advanceUntilIdle()

        assertNull(choices.chosen(FakeIds.HERO_MOVIE))
        assertNotNull(model.states.value.error)
        assertNull(model.states.value.opening)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun `using the source again forgets the release and plays`() = runTest(dispatcher) {
        val model = loaded(FakeIds.HERO_MOVIE)
        val effects = mutableListOf<TorrentsSideEffect>()
        model.observeSideEffects { effects += it }
        model.dispatch(TorrentsEvent.Choose(model.states.value.shown.first()))
        advanceUntilIdle()
        effects.clear()

        model.dispatch(TorrentsEvent.UseSource)
        advanceUntilIdle()

        assertNull(choices.chosen(FakeIds.HERO_MOVIE))
        assertNull(model.states.value.chosen)
        assertEquals(listOf<TorrentsSideEffect>(TorrentsSideEffect.Play(FakeIds.HERO_MOVIE, null)), effects)
    }

    @Test
    fun `failed search can be retried`() = runTest(dispatcher) {
        torrents.failSearch = true
        val model = loaded(FakeIds.HERO_MOVIE)
        assertEquals(TorrentsStage.FAILED, model.states.value.stage)

        torrents.failSearch = false
        model.dispatch(TorrentsEvent.Retry)
        advanceUntilIdle()

        assertEquals(TorrentsStage.READY, model.states.value.stage)
        assertEquals(false, torrents.queries.last().episodic)
    }

    @Test
    fun `changing the server returns to the connect step`() = runTest(dispatcher) {
        val model = loaded(FakeIds.HERO_MOVIE)

        model.dispatch(TorrentsEvent.ChangeServer)
        advanceUntilIdle()

        assertEquals(TorrentsStage.NO_SERVER, model.states.value.stage)
        assertTrue(model.states.value.releases.isEmpty())
    }

    private fun TestScope.loaded(itemId: Int, season: Int = VideoRef.NONE, number: Int = VideoRef.NONE) =
        TorrentsScreenModel(
            savedStateHandle = SavedStateHandle(mapOf("itemId" to itemId, "season" to season, "number" to number)),
            catalog = FakeCatalogRepository(backend),
            servers = servers,
            torrents = torrents,
            choices = choices,
            sources = FakeContentSourceRepository(),
        ).also { advanceUntilIdle() }
}
