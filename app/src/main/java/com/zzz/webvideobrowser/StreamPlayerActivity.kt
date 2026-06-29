package com.zzz.webvideobrowser

import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView

@UnstableApi
class StreamPlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stream_player)

        playerView = findViewById(R.id.playerView)

        val url = intent.getStringExtra("url") ?: return finish()
        val title = intent.getStringExtra("title") ?: "视频"
        val referer = intent.getStringExtra("referer")
        val origin = intent.getStringExtra("origin")
        val userAgent = intent.getStringExtra("userAgent") ?: "Mozilla/5.0"

        setTitle(title)

        val requestHeaders = mutableMapOf<String, String>()
        requestHeaders["User-Agent"] = userAgent

        if (!referer.isNullOrBlank()) {
            requestHeaders["Referer"] = referer
        }

        if (!origin.isNullOrBlank()) {
            requestHeaders["Origin"] = origin
        }

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setDefaultRequestProperties(requestHeaders)
            .setAllowCrossProtocolRedirects(true)

        val uri = Uri.parse(url)

        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMimeType(detectMimeType(url))
            .build()

        player = ExoPlayer.Builder(this).build().also { exo ->
            playerView.player = exo

            val source = when (mediaItem.localConfiguration?.mimeType) {
                MimeTypes.APPLICATION_M3U8 -> {
                    HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
                }

                MimeTypes.APPLICATION_MPD -> {
                    DashMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
                }

                else -> {
                    ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
                }
            }

            exo.setMediaSource(source)
            exo.prepare()
            exo.playWhenReady = true
        }
    }

    private fun detectMimeType(url: String): String? {
        val lower = url.lowercase()

        return when {
            ".m3u8" in lower || "m3u8" in lower -> MimeTypes.APPLICATION_M3U8
            ".mpd" in lower -> MimeTypes.APPLICATION_MPD
            ".mp4" in lower -> MimeTypes.VIDEO_MP4
            ".webm" in lower -> MimeTypes.VIDEO_WEBM
            else -> null
        }
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    private fun releasePlayer() {
        playerView.player = null
        player?.release()
        player = null
    }
}
