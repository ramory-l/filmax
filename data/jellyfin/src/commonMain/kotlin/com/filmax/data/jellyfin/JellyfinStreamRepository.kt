package com.filmax.data.jellyfin

import com.filmax.core.domain.catalog.model.MediaTrack
import com.filmax.core.domain.catalog.model.SubtitleTrack
import com.filmax.core.domain.catalog.model.VideoFile
import com.filmax.core.domain.catalog.model.ref
import com.filmax.core.domain.common.RequestResult
import com.filmax.core.domain.common.getOrNull
import com.filmax.core.domain.common.safeRequest
import com.filmax.core.domain.playback.StreamRepository
import com.filmax.data.jellyfin.mapper.JellyfinMapper
import com.filmax.data.jellyfin.remote.DeviceProfiles
import com.filmax.data.jellyfin.remote.dto.MediaSourceDto
import com.filmax.data.jellyfin.remote.dto.MediaStreamDto
import com.filmax.data.jellyfin.remote.dto.PlaybackInfoBody
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Потоки видео с домашнего сервера под конкретный запуск (`Items/{id}/PlaybackInfo`).
 *
 * Качество «как есть» — файл целиком, если плеер его потянет, а запасной вариант того же
 * качества — перекодирование, которое сервер выдаёт сам: плеер уходит на него при ошибке, как
 * у kino.pub при недоступном CDN. Ниже — ступени для слабой сети (Wi-Fi через стену): их сервер
 * перекодирует под заданный битрейт и сам подбирает разрешение.
 */
internal class JellyfinStreamRepository(
    private val context: JellyfinContext,
    private val videos: JellyfinVideos,
    private val tv: Boolean,
) : StreamRepository {

    override suspend fun playable(itemId: Int, track: MediaTrack): RequestResult<MediaTrack> = context.request { _ ->
        val video = videos.resolve(itemId, track.ref)
        val source = playbackInfo(video.guid, DeviceProfiles.MAX_BITRATE, original = true)
            ?: throw NoSuchElementException("Сервер не отдал видео (404)")
        val files = files(video.guid, source)
        check(files.isNotEmpty()) { "Сервер не может отдать это видео" }
        track.copy(
            files = files,
            audios = JellyfinMapper.audios(source.mediaStreams),
            subtitles = subtitles(source.mediaStreams),
            durationSeconds = source.runTimeTicks?.let(JellyfinMapper::ticksToSeconds)?.toInt()
                ?: track.durationSeconds,
        )
    }

    private suspend fun files(guid: String, source: MediaSourceDto): List<VideoFile> {
        val video = source.mediaStreams.orEmpty().firstOrNull { it.type == JellyfinMapper.STREAM_VIDEO }
        val label = JellyfinMapper.qualityLabel(video?.width, video?.height) ?: ORIGINAL
        val height = label.takeWhile(Char::isDigit).toIntOrNull() ?: Int.MAX_VALUE
        val remote = source.remoteUrl()
        // Файл как есть — только если сервер подтвердил, что плеер его сыграет: иначе фильм мог
        // бы пойти без звука, а такую ошибку плеер не увидит и на запасной поток не уйдёт.
        val direct = (remote ?: directUrl(guid, source)).takeIf { source.supportsDirectPlay == true }
        // Запасного перекодирования у внешней ссылки нет: сервер перекодировал бы тот же источник,
        // а сторож старта плеера уводил бы на него каждую раздачу, которая разгоняется дольше 8 с.
        val transcoded = source.transcodingUrl?.let(::absolute)?.takeUnless { remote != null && direct != null }
        val original = if (direct != null || transcoded != null) {
            VideoFile(quality = label, hls4 = null, hls = transcoded, http = direct)
        } else {
            null
        }
        return listOfNotNull(original) + lowerSteps(guid, height)
    }

    private suspend fun lowerSteps(guid: String, sourceHeight: Int): List<VideoFile> = coroutineScope {
        STEPS.filter { it.height < sourceHeight }.map { step ->
            async {
                // Ступень не собралась (перекодирование выключено для аккаунта) — её просто нет.
                val url = safeRequest { playbackInfo(guid, step.bitrate, original = false) }.getOrNull()?.transcodingUrl
                url?.let { VideoFile(quality = "${step.height}p", hls4 = null, hls = absolute(it), http = null) }
            }
        }.awaitAll().filterNotNull()
    }

    private suspend fun playbackInfo(guid: String, bitrate: Long, original: Boolean): MediaSourceDto? {
        val body = PlaybackInfoBody(
            userId = context.session.require().userId,
            maxStreamingBitrate = bitrate,
            deviceProfile = DeviceProfiles.of(tv),
            enableDirectPlay = original,
            enableDirectStream = original,
        )
        return context.api.playbackInfo(guid, body).mediaSources?.firstOrNull()
    }

    /**
     * Ссылка из `.strm` (поток TorrServer, IPTV): плеер идёт по ней сам, как официальные клиенты
     * Jellyfin. Через `/Videos/{id}/stream` сервер проксирует её и обрывает запрос, если источник
     * 100 с не отдал файл, — у раздачи, которая ещё ищет пиров, так бывает.
     */
    private fun MediaSourceDto.remoteUrl(): String? =
        path?.takeIf { protocol == HTTP_PROTOCOL && isRemote == true && it.startsWith("http") }

    /** Файл целиком, с перемоткой по Range. Ключ — в адресе: плеер ходит за потоком без заголовков. */
    private fun directUrl(guid: String, source: MediaSourceDto): String {
        val session = context.session.require()
        val mediaSource = source.id?.let { "&mediaSourceId=$it" }.orEmpty()
        return "${session.address}/Videos/$guid/stream?static=true$mediaSource&ApiKey=${session.token}"
    }

    private fun subtitles(streams: List<MediaStreamDto>?): List<SubtitleTrack> =
        streams.orEmpty()
            .filter { it.type == JellyfinMapper.STREAM_SUBTITLE && it.deliveryMethod == EXTERNAL }
            .mapNotNull { stream ->
                val url = stream.deliveryUrl?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                SubtitleTrack(lang = stream.language ?: UNDEFINED_LANGUAGE, url = absolute(url), shiftMs = 0)
            }

    /** Ссылки сервер отдаёт от своего корня; адрес сессии уже включает путь прокси, если он есть. */
    private fun absolute(url: String): String =
        if (url.startsWith("http")) url else "${context.session.require().address}/${url.removePrefix("/")}"

    private data class Step(val height: Int, val bitrate: Long)

    private companion object {
        const val ORIGINAL = "Оригинал"
        const val EXTERNAL = "External"
        const val HTTP_PROTOCOL = "Http"
        const val UNDEFINED_LANGUAGE = "und"

        /** Ступени ниже оригинала: битрейт, при котором сервер выбирает такое разрешение. */
        val STEPS = listOf(Step(height = 720, bitrate = 4_000_000L), Step(height = 480, bitrate = 1_500_000L))
    }
}
