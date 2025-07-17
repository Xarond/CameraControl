package com.Xarond.cameracontrol.controller

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Simple PTZ controller that issues HTTP GET commands to the camera.
 * The exact API depends on the device, but it commonly follows the
 * `/ptz` endpoint with query parameters.
 */
class PTZController(
    private val baseUrl: String,
    private val username: String? = null,
    private val password: String? = null
) {
    private val client = OkHttpClient()

    private fun send(command: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val builder = Request.Builder().url("$baseUrl/$command")
            if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) {
                builder.header("Authorization", Credentials.basic(username!!, password!!))
            }
            runCatching { client.newCall(builder.build()).execute() }
        }
    }

    fun moveUp() = send("ptz?move=up")
    fun moveDown() = send("ptz?move=down")
    fun moveLeft() = send("ptz?move=left")
    fun moveRight() = send("ptz?move=right")
    fun zoomIn() = send("ptz?zoom=in")
    fun zoomOut() = send("ptz?zoom=out")
}
