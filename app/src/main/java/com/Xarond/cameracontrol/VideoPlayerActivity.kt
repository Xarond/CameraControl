package com.Xarond.cameracontrol

import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.Xarond.cameracontrol.databinding.ActivityVideoPlayerBinding
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.PlayerView
import android.widget.Toast
import android.util.Log
import com.Xarond.cameracontrol.controller.PTZController

class VideoPlayerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityVideoPlayerBinding
    private lateinit var player: ExoPlayer
    private var ptzController: PTZController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val rtspUrl = intent.getStringExtra("rtspUrl") ?: return
        val ptzUrl = intent.getStringExtra("ptzUrl")

        player = ExoPlayer.Builder(this).build()
        binding.playerView.player = player

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(rtspUrl))
            .setMimeType(MimeTypes.APPLICATION_RTSP)
            .build()
        val mediaSource = RtspMediaSource.Factory()
            .setForceUseRtpTcp(true)
            .createMediaSource(mediaItem)
        player.setMediaSource(mediaSource)
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e("VideoPlayer", "Playback error", error)
                Toast.makeText(
                    this@VideoPlayerActivity,
                    "Błąd odtwarzania: ${error.errorCodeName}",
                    Toast.LENGTH_LONG
                ).show()
            }

            override fun onPlaybackStateChanged(state: Int) {
                Log.d("VideoPlayer", "Playback state changed: $state")
            }
        })
        player.prepare()
        player.play()

        if (ptzUrl != null) {
            ptzController = PTZController(ptzUrl)
            binding.ptzControls.btnUp.setOnClickListener { ptzController?.moveUp() }
            binding.ptzControls.btnDown.setOnClickListener { ptzController?.moveDown() }
            binding.ptzControls.btnLeft.setOnClickListener { ptzController?.moveLeft() }
            binding.ptzControls.btnRight.setOnClickListener { ptzController?.moveRight() }
            binding.ptzControls.btnZoomIn?.setOnClickListener { ptzController?.zoomIn() }
            binding.ptzControls.btnZoomOut?.setOnClickListener { ptzController?.zoomOut() }
        }
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
