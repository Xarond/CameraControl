package com.Xarond.cameracontrol.player

import android.content.Context
import android.view.SurfaceView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer

class RTSPPlayer(private val context: Context, private val surfaceView: SurfaceView) {

    private var player: ExoPlayer? = null

    fun play(rtspUrl: String) {
        val mediaItem = MediaItem.Builder()
            .setUri(rtspUrl)
            .setMimeType(MimeTypes.APPLICATION_RTSP)
            .build()

        player = ExoPlayer.Builder(context).build().apply {
            setMediaItem(mediaItem)
            setVideoSurfaceView(surfaceView)
            prepare()
            playWhenReady = true
        }
    }

    fun release() {
        player?.release()
    }
}