package com.Xarond.cameracontrol

import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.Xarond.cameracontrol.databinding.ActivityVideoPlayerBinding
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.ui.PlayerView
import com.Xarond.cameracontrol.controller.PTZController

class VideoPlayerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityVideoPlayerBinding
    private lateinit var player: SimpleExoPlayer
    private var ptzController: PTZController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val rtspUrl = intent.getStringExtra("rtspUrl") ?: return
        val ptzUrl = intent.getStringExtra("ptzUrl")

        player = SimpleExoPlayer.Builder(this).build()
        binding.playerView.player = player

        val mediaItem = MediaItem.fromUri(Uri.parse(rtspUrl))
        player.setMediaItem(mediaItem)
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
}
