package com.filmax.core.tv.designsystem

import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text

/** Бегущая строка названия: стартует не сразу — сначала зритель видит начало. */
private const val MARQUEE_DELAY_MS = 800
private const val MARQUEE_REPEAT_DELAY_MS = 1_200
private val MarqueeVelocity = 40.dp

/** Доля ширины, после которой правый край бегущей строки затухает. */
private const val FADE_EDGE_START = 0.85f

/**
 * Название карточки — всегда одна строка. Без фокуса — многоточие, в фокусе — бегущая строка
 * с затуханием правого края: виден весь заголовок, а карточка не растёт. Сколько раз она
 * пробегает — [cardTitleMarqueeIterations].
 */
@Composable
fun TvCardTitle(
    title: String,
    focused: Boolean,
    modifier: Modifier = Modifier,
    style: TextStyle = FxType.cardTitle,
) {
    val iterations =
        if (focused) cardTitleMarqueeIterations(LocalReducedMotion.current, LocalLightEffects.current) else null
    Text(
        title,
        style = style,
        color = LocalFxColors.current.text,
        maxLines = 1,
        softWrap = false,
        overflow = if (iterations != null) TextOverflow.Clip else TextOverflow.Ellipsis,
        modifier = modifier.then(
            if (iterations != null) {
                Modifier
                    .fadeEndEdge()
                    .basicMarquee(
                        iterations = iterations,
                        initialDelayMillis = MARQUEE_DELAY_MS,
                        repeatDelayMillis = MARQUEE_REPEAT_DELAY_MS,
                        spacing = MarqueeSpacing.fractionOfContainer(1f / 3f),
                        velocity = MarqueeVelocity,
                    )
            } else {
                Modifier
            },
        ),
    )
}

/**
 * Сколько раз пробегает название в фокусе; `null` — бегущей строки нет, название с многоточием.
 * В облегчённом режиме — один проход: название можно дочитать, а потом экран затихает. Бесконечная
 * строка не давала ему простаивать — ~50 кадров в секунду, пока карточка в фокусе.
 */
internal fun cardTitleMarqueeIterations(reducedMotion: Boolean, lightEffects: Boolean): Int? = when {
    reducedMotion -> null
    lightEffects -> 1
    else -> Int.MAX_VALUE
}

/** Затухание правого края: текст уходит под край мягко, а не обрубается. */
private fun Modifier.fadeEndEdge(): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(
                brush = Brush.horizontalGradient(FADE_EDGE_START to Color.Black, 1f to Color.Transparent),
                blendMode = BlendMode.DstIn,
            )
        }

/**
 * Надзаголовок секции: верхний регистр с трекингом и точкой акцента слева («● ВЫБОР РЕДАКЦИИ»).
 * [accent] = false — служебный заголовок группы (Профиль): без точки, приглушённым цветом.
 */
@Composable
fun TvOverline(text: String, modifier: Modifier = Modifier, accent: Boolean = true) {
    val colors = LocalFxColors.current
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (accent) Box(Modifier.size(6.dp).background(colors.accent, CircleShape))
        Text(
            text.uppercase(),
            style = FxType.overline,
            color = if (accent) colors.accentText else colors.textDim,
        )
    }
}

/** Строка меты через отступы: `Фэнтези · Приключения   2022   2 ч 11 мин`. Пустые части пропускаются. */
@Composable
fun TvMetaRow(parts: List<String>, modifier: Modifier = Modifier) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        parts.filter { it.isNotBlank() }.forEach { part ->
            Text(part, style = FxType.metaRow, color = LocalFxColors.current.textSecondary)
        }
    }
}
