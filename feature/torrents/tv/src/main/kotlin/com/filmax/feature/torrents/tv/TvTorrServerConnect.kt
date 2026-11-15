// Подключение TorrServer с пульта: слева адрес и кнопка, справа экранная клавиатура — как вход на
// домашний сервер. Логика — в общем TorrentsScreenModel.
package com.filmax.feature.torrents.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.filmax.core.presentation.input.KeyboardLayout
import com.filmax.core.presentation.input.addressKeyboardRows
import com.filmax.core.tv.designsystem.FxType
import com.filmax.core.tv.designsystem.LocalFxColors
import com.filmax.core.tv.designsystem.TvButton
import com.filmax.core.tv.designsystem.TvKeyboard
import com.filmax.core.tv.designsystem.TvKeyboardActions
import com.filmax.core.tv.designsystem.TvKeyboardLayouts
import com.filmax.core.tv.designsystem.TvKeyboardStyle
import com.filmax.core.tv.designsystem.TvScreenFocus
import com.filmax.core.tv.designsystem.TvSettingRow
import com.filmax.core.tv.designsystem.tvFocusGroup
import com.filmax.feature.torrents.common.TorrentsEvent
import com.filmax.feature.torrents.common.TorrentsState

/** Колонка шага: строка длиннее ~520dp заставляет глаз искать начало следующей. */
private val StepWidth = 520.dp

/** Клавиатура в одиннадцать колонок — по ширине её клавиш. */
private val KeyboardWidth = 440.dp
private val ColumnGap = 64.dp

/** Адрес TorrServer: где он и зачем — тем, кто видит раздел впервые. */
@Composable
internal fun TvTorrServerConnect(state: TorrentsState, onEvent: (TorrentsEvent) -> Unit, focus: TvScreenFocus) {
    val colors = LocalFxColors.current
    val keyboard = remember { FocusRequester() }
    Row(horizontalArrangement = Arrangement.spacedBy(ColumnGap)) {
        Column(Modifier.width(StepWidth), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "TorrServer качает раздачу и отдаёт её потоком, а ищет по трекерам через Jackett в своих " +
                    "настройках. Адрес — компьютер или приставка с TorrServer в этой сети и порт, обычно 8090.",
                style = FxType.body,
                color = colors.textSecondary,
            )
            // Куда «вправо» — задано явно: по геометрии ближе к строке переключатель раскладок, и
            // «ОК» там менял бы раскладку вместо набора.
            TvSettingRow(
                label = "Адрес TorrServer",
                value = "${state.address}▏",
                onClick = { onEvent(TorrentsEvent.Connect) },
                modifier = focus.item(ADDRESS_KEY).focusProperties { right = keyboard },
            )
            Row(Modifier.tvFocusGroup()) {
                TvButton(
                    text = if (state.connecting) "Подключаемся…" else "Подключить",
                    onClick = { onEvent(TorrentsEvent.Connect) },
                )
            }
            state.error?.let { Text(it, style = FxType.body, color = colors.error) }
        }
        AddressKeyboard(state.layout, onEvent, keyboard)
    }
}

@Composable
private fun AddressKeyboard(layout: KeyboardLayout, onEvent: (TorrentsEvent) -> Unit, firstKey: FocusRequester) {
    Column(Modifier.width(KeyboardWidth), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TvKeyboard(
            rows = addressKeyboardRows(layout),
            actions = TvKeyboardActions(
                onKey = { onEvent(TorrentsEvent.Type(it)) },
                // В адресе пробелов не бывает: клавиша пробела ничего не печатает.
                onSpace = {},
                onDelete = { onEvent(TorrentsEvent.Erase) },
            ),
            style = TvKeyboardStyle.Panel,
            modifier = Modifier.fillMaxWidth(),
            firstKey = firstKey,
        )
        TvKeyboardLayouts(
            labels = KeyboardLayout.entries.map { it.label },
            selected = layout.ordinal,
            onSelect = { onEvent(TorrentsEvent.SwitchLayout(KeyboardLayout.entries[it])) },
        )
    }
}
