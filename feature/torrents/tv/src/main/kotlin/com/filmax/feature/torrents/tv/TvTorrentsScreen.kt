package com.filmax.feature.torrents.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.filmax.core.navigation.Destination
import com.filmax.core.navigation.Navigator
import com.filmax.core.tv.designsystem.FilmaxTvTheme
import com.filmax.core.tv.designsystem.FxType
import com.filmax.core.tv.designsystem.LocalFxColors
import com.filmax.core.tv.designsystem.TvButton
import com.filmax.core.tv.designsystem.TvButtonStyle
import com.filmax.core.tv.designsystem.TvMessageCard
import com.filmax.core.tv.designsystem.TvMetrics
import com.filmax.core.tv.designsystem.TvOption
import com.filmax.core.tv.designsystem.TvOptionSheet
import com.filmax.core.tv.designsystem.TvOptionSheetState
import com.filmax.core.tv.designsystem.TvScreenFocus
import com.filmax.core.tv.designsystem.TvSheetHost
import com.filmax.core.tv.designsystem.preview.TvPreviews
import com.filmax.core.tv.designsystem.rememberTvScreenFocus
import com.filmax.core.tv.designsystem.tvFocusGroup
import com.filmax.core.tv.designsystem.tvSectionBackground
import com.filmax.feature.torrents.common.ReleaseFilterKind
import com.filmax.feature.torrents.common.TorrentsEvent
import com.filmax.feature.torrents.common.TorrentsScreenModel
import com.filmax.feature.torrents.common.TorrentsSideEffect
import com.filmax.feature.torrents.common.TorrentsStage
import com.filmax.feature.torrents.common.TorrentsState
import com.filmax.feature.torrents.common.optionLabels
import com.filmax.feature.torrents.common.selectedOption
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/** Ключи стартового фокуса: поле адреса, «Повторить» и строка раздачи по её ключу. */
internal const val ADDRESS_KEY = "address"
internal const val RETRY_KEY = "retry"
internal const val RELEASE_KEY = "release:"

/**
 * Торренты тайтла на ТВ. Раздачу выбирают здесь — экран сменяется плеером на том же видео, а
 * «Назад» из плеера возвращает в детали, а не сюда: выбор уже сделан.
 */
@Composable
fun TvTorrentsScreen(
    screenModel: TorrentsScreenModel = koinViewModel(),
    navigator: Navigator = koinInject(),
) {
    val state by screenModel.collectAsState()
    screenModel.collectSideEffect { effect ->
        when (effect) {
            is TorrentsSideEffect.Play -> navigator.replace(
                Destination.Player(itemId = effect.itemId, video = effect.video),
            )
        }
    }
    TvTorrentsContent(state, screenModel::dispatch)
}

/**
 * Экран из состояния — без Koin и навигатора, для превью. Над ним — панель вариантов фильтра:
 * закрываясь, она возвращает фокус на чип, который её открыл.
 */
@Composable
internal fun TvTorrentsContent(state: TorrentsState, onEvent: (TorrentsEvent) -> Unit) {
    val chips = remember { ReleaseFilterKind.entries.associateWith { FocusRequester() } }
    Box(Modifier.fillMaxSize().tvSectionBackground()) {
        TvSheetHost(
            open = state.filterOpen,
            onClose = { onEvent(TorrentsEvent.CloseFilter) },
            returnFocus = chips.getValue(state.filter),
            sheet = { TvOptionSheet(optionSheet(state)) { index -> onEvent(TorrentsEvent.ChooseOption(index)) } },
        ) {
            TorrentsBody(state, onEvent, chips)
        }
    }
}

@Composable
private fun TorrentsBody(
    state: TorrentsState,
    onEvent: (TorrentsEvent) -> Unit,
    chips: Map<ReleaseFilterKind, FocusRequester>,
) {
    val focus = rememberTvScreenFocus(startAt = startKey(state))
    // Содержимое сменилось целиком — фокус на его первый элемент, иначе он повис бы на исчезнувшем.
    // Адрес — в композиции, а не в эффекте: эффект запустился бы, когда элемент уже разместился.
    remember(state.stage) { focus.focusOn(startKey(state)) }
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = TvMetrics.SafeHorizontal, vertical = TvMetrics.SafeVertical)
            .then(focus.containerModifier),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TorrentsHeader(state, onEvent)
        when (state.stage) {
            TorrentsStage.NO_SERVER -> TvTorrServerConnect(state, onEvent, focus)
            TorrentsStage.SEARCHING -> Searching()
            TorrentsStage.FAILED -> Failure(state, onEvent, focus)
            TorrentsStage.READY -> TvReleases(state, onEvent, ReleasesFocus(focus, chips))
        }
    }
}

/** Куда ставить фокус, когда стадия сменилась: выбранная раздача, если она в списке, иначе первая. */
private fun startKey(state: TorrentsState): String? = when (state.stage) {
    TorrentsStage.NO_SERVER -> ADDRESS_KEY
    TorrentsStage.FAILED -> RETRY_KEY
    TorrentsStage.SEARCHING -> null
    TorrentsStage.READY -> {
        val chosen = state.shown.firstOrNull { it.key == state.chosen?.key } ?: state.shown.firstOrNull()
        chosen?.let { "$RELEASE_KEY${it.key}" }
    }
}

/** Тайтл слева, справа — сервер и действия над раздачами: они нужны, только когда список уже есть. */
@Composable
private fun TorrentsHeader(state: TorrentsState, onEvent: (TorrentsEvent) -> Unit) {
    val colors = LocalFxColors.current
    Row(verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("ТОРРЕНТЫ", style = FxType.overline, color = colors.accentText)
            Text(state.title, style = FxType.screenTitle, color = colors.text, maxLines = 1)
            if (state.subtitle.isNotEmpty()) Text(state.subtitle, style = FxType.meta, color = colors.textSecondary)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.stage == TorrentsStage.READY) ReleaseActions(state, onEvent)
            state.server?.let { server ->
                Text(
                    "TorrServer · ${server.address.substringAfter("://")}",
                    style = FxType.meta,
                    color = colors.textDim,
                )
            }
        }
    }
}

/** Поиск идёт по всем трекерам Jackett разом и бывает долгим: подпись объясняет ожидание. */
@Composable
private fun Searching() {
    val colors = LocalFxColors.current
    Row(
        Modifier.padding(top = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CircularProgressIndicator(color = colors.accent)
        Text("Ищем раздачи на трекерах…", style = FxType.body, color = colors.textSecondary)
    }
}

/** Сбой загрузки или поиска: повтор и смена сервера — частая причина в неверном адресе. */
@Composable
private fun Failure(state: TorrentsState, onEvent: (TorrentsEvent) -> Unit, focus: TvScreenFocus) {
    TvMessageCard(
        title = "Торренты не загрузились",
        message = state.error.orEmpty(),
        modifier = Modifier.padding(top = 12.dp),
    )
    Row(Modifier.tvFocusGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TvButton(text = "Повторить", onClick = { onEvent(TorrentsEvent.Retry) }, modifier = focus.item(RETRY_KEY))
        if (state.server != null) {
            TvButton(
                text = "Другой сервер",
                onClick = { onEvent(TorrentsEvent.ChangeServer) },
                style = TvButtonStyle.Secondary,
            )
        }
    }
}

/** Панель вариантов открытого фильтра. */
private fun optionSheet(state: TorrentsState): TvOptionSheetState = TvOptionSheetState(
    overline = "Торренты",
    title = state.filter.label,
    options = state.optionLabels(state.filter).map { TvOption(it) },
    selectedIndex = state.selectedOption(state.filter),
)

private class TorrentsStatePreviews : PreviewParameterProvider<TorrentsState> {
    override val values = TorrentsSamples.all.asSequence()
}

@TvPreviews
@Composable
private fun TvTorrentsScreenPreview(@PreviewParameter(TorrentsStatePreviews::class) state: TorrentsState) {
    FilmaxTvTheme { TvTorrentsContent(state = state, onEvent = {}) }
}
