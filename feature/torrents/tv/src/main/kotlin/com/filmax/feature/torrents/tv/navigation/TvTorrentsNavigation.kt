package com.filmax.feature.torrents.tv.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.filmax.feature.torrents.common.navigation.TorrentsRoute
import com.filmax.feature.torrents.tv.TvTorrentsScreen

/** Выбор раздачи — push-экран из деталей, без меню, как и сами детали. */
fun NavGraphBuilder.tvTorrentsScreen() {
    composable<TorrentsRoute> { TvTorrentsScreen() }
}
