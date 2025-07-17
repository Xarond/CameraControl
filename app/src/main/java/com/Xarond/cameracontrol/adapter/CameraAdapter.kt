package com.Xarond.cameracontrol.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.Xarond.cameracontrol.databinding.ItemCameraBinding
import com.Xarond.cameracontrol.model.CameraModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer

class CameraAdapter(
    private val cameras: MutableList<CameraModel>,
    private val onItemClick: (CameraModel) -> Unit,
    private val onItemLongClick: (Int) -> Unit
) : RecyclerView.Adapter<CameraAdapter.CameraViewHolder>() {

    inner class CameraViewHolder(val binding: ItemCameraBinding) :
        RecyclerView.ViewHolder(binding.root) {
        var player: ExoPlayer? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CameraViewHolder {
        val binding = ItemCameraBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CameraViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CameraViewHolder, position: Int) {
        val camera = cameras[position]
        holder.binding.tvCameraName.text = camera.name

        if (holder.player == null) {
            holder.player = ExoPlayer.Builder(holder.binding.playerViewPreview.context).build().apply {
                val item = MediaItem.Builder()
                    .setUri(camera.rtspUrl)
                    .setMimeType(MimeTypes.APPLICATION_RTSP)
                    .build()
                setMediaItem(item)
                prepare()
                playWhenReady = true
            }
            holder.binding.playerViewPreview.player = holder.player
        }

        holder.binding.root.setOnClickListener { onItemClick(camera) }
        holder.binding.root.setOnLongClickListener {
            onItemLongClick(holder.bindingAdapterPosition)
            true
        }
    }

    override fun onViewRecycled(holder: CameraViewHolder) {
        holder.player?.release()
        holder.player = null
        super.onViewRecycled(holder)
    }

    override fun getItemCount(): Int = cameras.size
}
