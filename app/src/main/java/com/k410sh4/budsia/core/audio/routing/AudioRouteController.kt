package com.k410sh4.budsia.core.audio.routing

data class PreparedAudioRoute(
    val success: Boolean,
    val inputDeviceId: Int,
    val outputDeviceId: Int,
    val communicationMode: Boolean,
    val communicationDeviceId: Int? = null,
    val message: String? = null
)

interface AudioRouteController {
    fun prepare(
        inputDeviceId: Int,
        outputDeviceId: Int
    ): PreparedAudioRoute

    fun release()
}
