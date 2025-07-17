package com.Xarond.cameracontrol.storage

import android.content.Context
import com.Xarond.cameracontrol.model.CameraModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object CameraStorage {
    private const val PREFS_NAME = "camera_prefs"
    private const val KEY_CAMERAS = "saved_cameras"

    fun saveCameras(context: Context, cameras: List<CameraModel>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = Gson().toJson(cameras)
        prefs.edit().putString(KEY_CAMERAS, json).apply()
    }

    fun loadCameras(context: Context): List<CameraModel> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_CAMERAS, null)
        return if (json != null) {
            val type = object : TypeToken<List<CameraModel>>() {}.type
            Gson().fromJson(json, type)
        } else {
            emptyList()
        }
    }
}