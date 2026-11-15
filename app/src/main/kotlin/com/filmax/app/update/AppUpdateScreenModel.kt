package com.filmax.app.update

import com.filmax.app.BuildConfig
import com.filmax.core.presentation.BaseScreenModel
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Текущую сборку обновляет релизный APK: это release Filmax и «Плёнка» — их CI публикует в
 * Releases. Раньше здесь сверялся пакет com.filmax.app, и «Плёнка» со своим пакетом обновлений
 * не видела.
 */
private val IS_RELEASE_BUILD = BuildConfig.SELF_UPDATE

/** Шаг публикации прогресса: чаще обновлять state незачем — это прыжок на main на каждый чанк. */
private const val PROGRESS_STEP = 0.01f

/**
 * Проверка и скачивание обновлений приложения (GitHub Releases, см. [GitHubUpdateRepository]).
 *
 * Debug, demo, mock и benchmark стоят под другими applicationId (`.debug`, `.demo`…) — для них
 * release-APK не обновление, а вторая установка, поэтому проверка выключена (`BuildConfig.SELF_UPDATE`).
 */
class AppUpdateScreenModel(
    private val repository: GitHubUpdateRepository,
    private val ioDispatcher: CoroutineDispatcher,
) : BaseScreenModel<AppUpdateState, AppUpdateSideEffect, AppUpdateEvent>(AppUpdateState()) {

    init {
        onFetchData()
    }

    override fun onEvent(event: AppUpdateEvent) {
        when (event) {
            AppUpdateEvent.Check -> check()
            AppUpdateEvent.Download -> download()
            AppUpdateEvent.Install -> install()
            AppUpdateEvent.Dismiss -> dismiss()
        }
    }

    override fun onFetchData() {
        if (!IS_RELEASE_BUILD) return
        screenModelScope(ioDispatcher) {
            // Ошибки проверки молчаливые: обновление — фон, а не повод для модалки при старте.
            val update = repository.latestUpdate() ?: return@screenModelScope
            updateState { it.copy(update = update) }
        }
    }

    /**
     * Ручная проверка. В отличие от стартовой она идёт в любой сборке и отчитывается о результате:
     * пользователь нажал сам, и тишина в ответ читается как сломанная кнопка. Заодно снимает
     * «Позже» — раз человек пришёл за обновлением, прятать найденное больше не нужно.
     *
     * В debug/demo проверка честно называет свежую версию, но ставить её не предлагает
     * ([AppUpdateState.installable]): у этих сборок свой applicationId.
     */
    private fun check() {
        screenModelScope(ioDispatcher) {
            val snapshot = state
            if (snapshot.checking) return@screenModelScope
            updateState {
                it.copy(checking = true, upToDate = false, dismissed = false, downloadError = false)
            }
            val update = repository.latestUpdate()
            updateState {
                it.copy(
                    checking = false,
                    update = update,
                    upToDate = update == null,
                    installable = IS_RELEASE_BUILD,
                )
            }
        }
    }

    private fun download() {
        screenModelScope(ioDispatcher) {
            val snapshot = state
            val update = snapshot.update ?: return@screenModelScope
            updateState { it.copy(downloading = true, progress = 0f, downloadError = false) }
            var lastReported = 0f
            val result = runCatching {
                repository.downloadApk(update) { fraction ->
                    if (fraction - lastReported >= PROGRESS_STEP) {
                        lastReported = fraction
                        updateState { it.copy(progress = fraction) }
                    }
                }
            }
            result.fold(
                onSuccess = { apk ->
                    updateState { it.copy(downloading = false, progress = 1f, downloadedApk = apk) }
                    postSideEffect(AppUpdateSideEffect.LaunchInstaller(apk))
                },
                onFailure = { _ ->
                    updateState { it.copy(downloading = false, downloadError = true) }
                },
            )
        }
    }

    private fun install() {
        screenModelScope {
            val snapshot = state
            snapshot.downloadedApk?.let { postSideEffect(AppUpdateSideEffect.LaunchInstaller(it)) }
        }
    }

    private fun dismiss() {
        screenModelScope {
            updateState { it.copy(dismissed = true, upToDate = false) }
        }
    }
}
