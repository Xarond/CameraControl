package com.Xarond.cameracontrol

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.Xarond.cameracontrol.databinding.ActivityMainBinding
import com.Xarond.cameracontrol.model.CameraModel
import com.Xarond.cameracontrol.VideoPlayerActivity
import com.Xarond.cameracontrol.adapter.CameraAdapter
import com.Xarond.cameracontrol.storage.CameraStorage
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val cameras = mutableListOf<CameraModel>()
    private lateinit var adapter: CameraAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameras.addAll(CameraStorage.loadCameras(this))

        adapter = CameraAdapter(cameras, onItemClick = { camera ->
            val intent = Intent(this, VideoPlayerActivity::class.java)
            intent.putExtra("rtspUrl", camera.rtspUrl)
            camera.ptzUrl?.let { intent.putExtra("ptzUrl", it) }
            startActivity(intent)
        }, onItemLongClick = { index ->
            showRemoveDialog(index)
        })

        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = GridLayoutManager(this, 2)

        binding.fabAddCamera.setOnClickListener {
            showAddCameraDialog()
        }
    }

    private fun showAddCameraDialog() {
        val dialogView = layoutInflater.inflate(android.R.layout.simple_list_item_2, null)
        val nameInput = EditText(this)
        nameInput.hint = "Nazwa kamery"
        val urlInput = EditText(this)
        urlInput.hint = "RTSP URL"
        val ptzInput = EditText(this)
        ptzInput.hint = "PTZ URL (opcjonalnie)"

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.addView(nameInput)
        layout.addView(urlInput)
        layout.addView(ptzInput)

        AlertDialog.Builder(this)
            .setTitle("Dodaj kamerę")
            .setView(layout)
            .setPositiveButton("Dodaj") { _, _ ->
                val name = nameInput.text.toString()
                val url = urlInput.text.toString()
                val ptz = ptzInput.text.toString()

                if (url.isNotBlank()) {
                    val camera = CameraModel(name, url, ptz.ifBlank { null })
                    cameras.add(camera)
                    CameraStorage.saveCameras(this, cameras)
                    adapter.notifyItemInserted(cameras.size - 1)
                } else {
                    Toast.makeText(this, "Adres RTSP nie może być pusty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Anuluj", null)
            .show()
    }

    private fun showRemoveDialog(index: Int) {
        AlertDialog.Builder(this)
            .setTitle("Usuń kamerę")
            .setMessage("Czy na pewno chcesz usunąć kamerę?")
            .setPositiveButton("Usuń") { _, _ ->
                cameras.removeAt(index)
                CameraStorage.saveCameras(this, cameras)
                adapter.notifyItemRemoved(index)
            }
            .setNegativeButton("Anuluj", null)
            .show()
    }
}
