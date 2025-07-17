package com.Xarond.cameracontrol.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class CameraModel(
    val name: String,
    val rtspUrl: String,
    val onvifPort: Int? = null,
    val username: String? = null,
    val password: String? = null
) : Parcelable