// Список раздач: чипы фильтров с панелями вариантов и ленивый список строк раздач.
package com.filmax.feature.torrents.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.filmax.core.domain.torrent.TorrentRelease
import com.filmax.core.tv.designsystem.FxType
import com.filmax.core.tv.designsystem.LocalFxColors
import com.filmax.core.tv.designsystem.TvChip
import com.filmax.core.tv.designsystem.TvChipStyle
import com.filmax.core.tv.designsystem.TvMessageCard
import com.filmax.core.tv.designsystem.TvReleaseRow
import com.filmax.core.tv.designsystem.TvReleaseRowModel
import com.filmax.core.tv.designsystem.TvScreenFocus
import com.filmax.core.tv.designsystem.rememberTvLazyFocus
import com.filmax.core.tv.designsystem.tvFocusGroup
import com.filmax.core.tv.designsystem.tvLazyFocusGroup
import com.filmax.core.tv.designsystem.tvLazyFocusItem
import com.filmax.feature.torrents.common.ReleaseFilterKind
import com.filmax.feature.torrents.common.ReleaseLabels
import com.filmax.feature.torrents.common.TorrentsEvent
import com.filmax.feature.torrents.common.TorrentsState
import com.filmax.feature.torrents.common.filterLabel
import com.filmax.feature.torrents.common.hasChoice
import com.filmax.feature.torrents.common.selectedOption

/** Фокус экрана и чипы фильтров: на чип возвращается закрытая панель вариантов. */
internal class ReleasesFocus(val screen: TvScreenFocus, val chips: Map<ReleaseFilterKind, FocusRequester>)

/** Строки в фокусе увеличиваются на 2%: отступ списка, чтобы крайние не обрезались. */
private val ListPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)

@Composable
internal fun ColumnScope.TvReleases(state: TorrentsState, onEvent: (TorrentsEvent) -> Unit, focus: ReleasesFocus) {
    FilterChips(state, onEvent, focus.chips)
    state.error?.let { Text(it, style = FxType.body, color = LocalFxColors.current.error) }
    when {
        state.releases.isEmpty() -> TvMessageCard(
            title = "Раздач не нашлось",
            message = "Трекеры в Jackett не знают «${state.title}» этого года.",
            hint = "Добавьте трекеров в Jackett или проверьте, что он запущен.",
        )
        state.shown.isEmpty() -> TvMessageCard(
            title = "Под эти фильтры раздач нет",
            message = "Выберите другое качество, озвучку или сезон.",
        )
        else -> ReleaseList(state, onEvent, focus.screen, Modifier.weight(1f))
    }
}

/**
 * Чипы фильтров, у которых есть из чего выбирать; выбранный фильтр залит акцентом. Действий в
 * этом ряду нет: «вверх» со списка ведёт в ближайший к центру строки чип, и нажатый по привычке
 * OK должен открыть панель, а не запустить источник или забыть сервер.
 */
@Composable
private fun FilterChips(
    state: TorrentsState,
    onEvent: (TorrentsEvent) -> Unit,
    chips: Map<ReleaseFilterKind, FocusRequester>,
) {
    Row(Modifier.fillMaxWidth().tvFocusGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ReleaseFilterKind.entries.filter(state::hasChoice).forEach { kind ->
            TvChip(
                label = state.filterLabel(kind),
                selected = kind != ReleaseFilterKind.SORT && state.selectedOption(kind) != 0,
                onClick = { onEvent(TorrentsEvent.OpenFilter(kind)) },
                modifier = Modifier.focusRequester(chips.getValue(kind)),
                trailingIcon = Icons.Rounded.ExpandMore,
            )
        }
    }
}

/**
 * Действия над раздачами — в шапке, над адресом сервера: вернуть источник, если тайтл играет из
 * раздачи, и сменить сервер.
 */
@Composable
internal fun ReleaseActions(state: TorrentsState, onEvent: (TorrentsEvent) -> Unit) {
    Row(Modifier.tvFocusGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val sourceName = state.sourceName
        if (state.chosen != null && sourceName != null) {
            TvChip(
                label = "Играть с $sourceName",
                selected = false,
                onClick = { onEvent(TorrentsEvent.UseSource) },
                style = TvChipStyle.Outlined,
            )
        }
        TvChip(
            label = "Другой сервер",
            selected = false,
            onClick = { onEvent(TorrentsEvent.ChangeServer) },
            style = TvChipStyle.Outlined,
        )
    }
}

/**
 * Раздачи под фильтрами. Список открывается на выбранной раздаче — по ней видно, что сейчас
 * играет, — а вход в список сверху ведёт на неё же.
 */
@Composable
private fun ReleaseList(
    state: TorrentsState,
    onEvent: (TorrentsEvent) -> Unit,
    focus: TvScreenFocus,
    modifier: Modifier,
) {
    val shown = state.shown
    val chosenIndex = shown.indexOfFirst { it.key == state.chosen?.key }.takeIf { it >= 0 }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = chosenIndex ?: 0)
    val listFocus = rememberTvLazyFocus()
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth().tvLazyFocusGroup(listFocus, listState, preferred = chosenIndex),
        contentPadding = ListPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(shown, key = { _, release -> release.key }) { index, release ->
            TvReleaseRow(
                model = release.rowModel(
                    chosen = release.key == state.chosen?.key,
                    busy = release.link == state.opening,
                ),
                onClick = { onEvent(TorrentsEvent.Choose(release)) },
                modifier = Modifier.tvLazyFocusItem(listFocus, index).then(focus.item("$RELEASE_KEY${release.key}")),
            )
        }
    }
}

private fun TorrentRelease.rowModel(chosen: Boolean, busy: Boolean) = TvReleaseRowModel(
    title = title,
    badges = ReleaseLabels.badges(traits),
    voices = ReleaseLabels.voices(traits),
    meta = ReleaseLabels.meta(this),
    size = ReleaseLabels.size(sizeBytes),
    seeders = seeders,
    chosen = chosen,
    busy = busy,
)
