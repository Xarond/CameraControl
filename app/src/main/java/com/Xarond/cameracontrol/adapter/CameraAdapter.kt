package com.Xarond.cameracontrol.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.Xarond.cameracontrol.databinding.ItemCameraBinding
import com.Xarond.cameracontrol.model.CameraModel

class CameraAdapter(
    private val cameras: List<CameraModel>,
    private val onItemClick: (CameraModel) -> Unit
) : RecyclerView.Adapter<CameraAdapter.CameraViewHolder>() {

    inner class CameraViewHolder(val binding: ItemCameraBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CameraViewHolder {
        val binding = ItemCameraBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CameraViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CameraViewHolder, position: Int) {
        val camera = cameras[position]
        holder.binding.tvCameraName.text = camera.name
        holder.binding.root.setOnClickListener {
            onItemClick(camera)
        }
    }

    override fun getItemCount(): Int = cameras.size
}
