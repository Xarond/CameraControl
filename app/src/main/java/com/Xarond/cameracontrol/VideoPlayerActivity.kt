package com.Xarond.cameracontrol

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import com.Xarond.cameracontrol.controller.PTZController
import com.Xarond.cameracontrol.databinding.ActivityVideoPlayerBinding

class VideoPlayerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityVideoPlayerBinding
    private lateinit var player: ExoPlayer
    private var ptzController: PTZController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val rtspUrl = intent.getStringExtra("rtspUrl") ?: run {
            Toast.makeText(this, "Brak adresu RTSP", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val ptzPort  = intent.getIntExtra("onvifPort", 8899).takeIf { it != 0 } ?: 8899
        val username = intent.getStringExtra("username") ?: ""
        val password = intent.getStringExtra("password") ?: ""

        // ExoPlayer setup
        player = ExoPlayer.Builder(this).build().also { binding.playerView.player = it }
        player.trackSelectionParameters = TrackSelectionParameters.Builder(this)
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
            .build()

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
                Log.e("VideoPlayer", "RTSP error", error)
                Toast.makeText(this@VideoPlayerActivity, "Błąd odtwarzania: ${error.errorCodeName}", Toast.LENGTH_LONG).show()
            }
        })
        player.prepare()
        player.play()

        Uri.parse(rtspUrl).host?.takeIf { it.isNotBlank() }?.let { ip ->
            ptzController = PTZController(ip, ptzPort, this, username, password)
            binding.ptzControls.apply {
                btnLeft.setOnClickListener  { ptzController?.moveLeft()  }
                btnRight.setOnClickListener { ptzController?.moveRight() }
                btnUp.setOnClickListener    { ptzController?.moveUp()    }
                btnDown.setOnClickListener  { ptzController?.moveDown()  }
                btnZoomIn.setOnClickListener { ptzController?.zoomIn()  }
                btnZoomOut.setOnClickListener{ ptzController?.zoomOut() }
            }
        } ?: Toast.makeText(this, "Nie można wyciągnąć IP z RTSP", Toast.LENGTH_LONG).show()
    }

    override fun onStop()    { super.onStop(); player.pause() }
    override fun onStart()   { super.onStart(); player.play() }
    override fun onDestroy() { player.release(); super.onDestroy() }
    override fun onSupportNavigateUp(): Boolean { onBackPressedDispatcher.onBackPressed(); return true }
}