package com.filmax.core.tv.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.filmax.core.tv.designsystem.preview.TvComponentPreview

/** Колонка размера и сидов: «40 ГБ» и «↑ 1200» в одну ширину у всех строк — цифры встают столбцом. */
private val StatsWidth = 76.dp
private val BusySize = 20.dp
private const val IDLE_FILL_ALPHA = 0.05f
private const val FOCUSED_FILL_ALPHA = 0.14f

/** Что строка знает о раздаче — готовые подписи, считает их вызывающий. */
@Immutable
data class TvReleaseRowModel(
    val title: String,
    /** «4K», «HDR», «HEVC», «WEB-DL» — контурными значками перед озвучкой. */
    val badges: List<String>,
    val voices: String?,
    /** Трекер и дата: «NoNaMe Club · 8 янв 2025». */
    val meta: String,
    val size: String?,
    /** Сиды; null — трекер не прислал. Без сидов раздача не стартует, поэтому ноль приглушён. */
    val seeders: Int?,
    /** Тайтл играет из этой раздачи: галочка. */
    val chosen: Boolean = false,
    /** TorrServer открывает раздачу: вместо галочки — ожидание. */
    val busy: Boolean = false,
)

/**
 * Раздача в списке торрентов: название, под ним значки качества, озвучка и трекер, справа размер
 * и сиды. Название трекера длинное и пёстрое, поэтому главное — во второй строке значками, а
 * название обрезается. Фокус — как у широких строк: 1.02 и рамка со свечением.
 */
@Composable
fun TvReleaseRow(model: TvReleaseRowModel, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalFxColors.current
    TvFocusSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        style = TvFocusStyle.Row,
        colors = TvSurfaceColors(
            container = Color.White.copy(alpha = IDLE_FILL_ALPHA),
            content = colors.text,
            focusedContainer = Color.White.copy(alpha = FOCUSED_FILL_ALPHA),
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    model.title,
                    style = FxType.button,
                    color = if (model.chosen) colors.accentText else colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ReleaseDetails(model)
            }
            ReleaseStats(model)
            when {
                model.busy -> CircularProgressIndicator(
                    color = colors.accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(BusySize),
                )
                model.chosen -> SelectionMark(selected = true)
            }
        }
    }
}

/** Значки, озвучка и трекер одной строкой: что важнее — левее, хвост обрезается. */
@Composable
private fun ReleaseDetails(model: TvReleaseRowModel) {
    val colors = LocalFxColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        model.badges.forEach { FormatBadge(it) }
        val details = listOfNotNull(model.voices, model.meta.ifBlank { null }).joinToString(" · ")
        Text(
            details,
            style = FxType.metaOnImage,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun ReleaseStats(model: TvReleaseRowModel) {
    val colors = LocalFxColors.current
    Column(Modifier.width(StatsWidth), horizontalAlignment = Alignment.End) {
        Text(model.size.orEmpty(), style = FxType.button, color = colors.text, maxLines = 1)
        model.seeders?.let { seeders ->
            Text(
                "↑ $seeders",
                style = FxType.metaOnImage,
                color = if (seeders > 0) colors.textSecondary else colors.textDim,
                maxLines = 1,
            )
        }
    }
}

@TvComponentPreview
@Composable
private fun TvReleaseRowPreview() {
    FilmaxTvTheme {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvReleaseRow(
                model = TvReleaseRowModel(
                    title = "Сёгун / Shogun / S1E1-10 of 10 (2024) WEB-DL [H.265/2160p] [4K, HDR10, Dolby Vision]",
                    badges = listOf("4K", "HDR", "DV", "HEVC", "WEB-DL"),
                    voices = "Red Head Sound, LostFilm, HDrezka",
                    meta = "NoNaMe Club · 8 янв 2025",
                    size = "40 ГБ",
                    seeders = 120,
                    chosen = true,
                ),
                onClick = {},
            )
            TvReleaseRow(
                model = TvReleaseRowModel(
                    title = "Сёгун / Shogun, S1E1-10 of 10 (2024) WEB-DL 1080p | HDrezka Studio, LostFilm",
                    badges = listOf("1080p", "WEB-DL"),
                    voices = "HDrezka, LostFilm",
                    meta = "BigFANGroup · 23 апр 2024",
                    size = "21,4 ГБ",
                    seeders = 0,
                    busy = true,
                ),
                onClick = {},
            )
        }
    }
}
