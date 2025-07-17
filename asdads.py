import zipfile
import os

# Base path for building the project structure
base_path = "/mnt/data/MyCameraApp"

# File content templates (short versions, will be filled later with real code)
file_contents = {
    "app/src/main/java/com/example/mycameraapp/MainActivity.kt": """package com.example.mycameraapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mycameraapp.adapter.CameraAdapter
import com.example.mycameraapp.model.CameraModel
import com.example.mycameraapp.network.OnvifDiscoveryService

class MainActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private val cameraList = mutableListOf<CameraModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, 2)

        OnvifDiscoveryService.discoverCameras { discovered ->
            runOnUiThread {
                cameraList.clear()
                cameraList.addAll(discovered)
                recyclerView.adapter = CameraAdapter(cameraList) { camera ->
                    val intent = Intent(this, CameraPlayerActivity::class.java)
                    intent.putExtra("rtspUrl", camera.rtspUrl)
                    startActivity(intent)
                }
            }
        }
    }
}
""",
    "app/src/main/java/com/example/mycameraapp/CameraPlayerActivity.kt": """package com.example.mycameraapp

import android.os.Bundle
import android.view.SurfaceView
import androidx.appcompat.app.AppCompatActivity
import com.example.mycameraapp.player.RTSPPlayer

class CameraPlayerActivity : AppCompatActivity() {
    private lateinit var player: RTSPPlayer
    private lateinit var surfaceView: SurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_player)

        val rtspUrl = intent.getStringExtra("rtspUrl") ?: return
        surfaceView = findViewById(R.id.surfaceView)
        player = RTSPPlayer(this, surfaceView)
        player.play(rtspUrl)
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }
}
""",
    "app/src/main/java/com/example/mycameraapp/model/CameraModel.kt": """package com.example.mycameraapp.model

data class CameraModel(
    val name: String,
    val rtspUrl: String,
    val hasPTZ: Boolean
)
""",
    "app/src/main/java/com/example/mycameraapp/network/OnvifDiscoveryService.kt": """package com.example.mycameraapp.network

import com.example.mycameraapp.model.CameraModel

object OnvifDiscoveryService {
    fun discoverCameras(callback: (List<CameraModel>) -> Unit) {
        // Dummy implementation
        callback(listOf(
            CameraModel("Example Camera", "rtsp://192.168.1.100:554/stream", true)
        ))
    }
}
""",
    "app/src/main/java/com/example/mycameraapp/player/RTSPPlayer.kt": """package com.example.mycameraapp.player

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

        player = ExoPlayer.Builder(context).build().also {
            it.setMediaItem(mediaItem)
            it.setVideoSurfaceView(surfaceView)
            it.prepare()
            it.playWhenReady = true
        }
    }

    fun release() {
        player?.release()
    }
}
""",
    "app/src/main/res/layout/activity_main.xml": """<?xml version="1.0" encoding="utf-8"?>
<androidx.recyclerview.widget.RecyclerView xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/recyclerView"
    android:layout_width="match_parent"
    android:layout_height="match_parent"/>
""",
    "app/src/main/res/layout/activity_camera_player.xml": """<?xml version="1.0" encoding="utf-8"?>
<SurfaceView xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/surfaceView"
    android:layout_width="match_parent"
    android:layout_height="match_parent"/>
""",
    "app/src/main/res/values/colors.xml": """<resources>
    <color name="blue">#2196F3</color>
    <color name="blue_grey">#607D8B</color>
    <color name="black">#000000</color>
</resources>
""",
    "app/src/main/res/values/themes.xml": """<resources xmlns:tools="http://schemas.android.com/tools">
    <style name="Theme.MyCameraApp" parent="Theme.Material3.DayNight.DarkActionBar">
        <item name="colorPrimary">@color/blue</item>
        <item name="colorSecondary">@color/blue_grey</item>
    </style>
</resources>
""",
    "app/src/main/AndroidManifest.xml": """<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.mycameraapp">

    <uses-permission android:name="android.permission.INTERNET"/>
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE"/>
    <uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE"/>

    <application
        android:theme="@style/Theme.MyCameraApp"
        android:label="MyCameraApp">
        <activity android:name=".CameraPlayerActivity"/>
        <activity android:name=".MainActivity">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>
    </application>
</manifest>
""",
    "app/build.gradle": """plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}

android {
    compileSdk 34

    defaultConfig {
        applicationId "com.example.mycameraapp"
        minSdk 21
        targetSdk 34
        versionCode 1
        versionName "1.0"
    }

    buildTypes {
        release {
            minifyEnabled false
        }
    }
}

dependencies {
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.11.0'
    implementation 'androidx.media3:media3-exoplayer:1.3.0'
    implementation 'com.github.MilosKozak:Onvif-Java:1.0.5'
}
""",
    "build.gradle": """// Top-level build file
buildscript {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:8.1.1'
        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.10"
    }
}
""",
    "settings.gradle": """pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
rootProject.name = "MyCameraApp"
include ':app'
"""
}

# Write files to disk
for path, content in file_contents.items():
    full_path = os.path.join(base_path, path)
    os.makedirs(os.path.dirname(full_path), exist_ok=True)
    with open(full_path, "w") as f:
        f.write(content)

# Zip the folder
zip_path = "/mnt/data/MyCameraApp.zip"
with zipfile.ZipFile(zip_path, 'w') as zipf:
    for root, dirs, files in os.walk(base_path):
        for file in files:
            file_path = os.path.join(root, file)
            arcname = os.path.relpath(file_path, base_path)
            zipf.write(file_path, arcname)

zip_path
