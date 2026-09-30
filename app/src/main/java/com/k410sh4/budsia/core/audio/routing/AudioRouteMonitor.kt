package com.k410sh4.budsia.core.audio.routing

import kotlinx.coroutines.flow.StateFlow

data class AudioDeviceDescriptor(
    val id: Int,
    val productName: String,
    val type: Int,
    val typeLabel: String,
    val isInput: Boolean,
    val isOutput: Boolean
)

data class AudioDeviceCatalog(
    val inputs: List<AudioDeviceDescriptor> = emptyList(),
    val outputs: List<AudioDeviceDescriptor> = emptyList()
)

interface AudioRouteMonitor {
    val devices: StateFlow<AudioDeviceCatalog>

    fun inputLabel(deviceId: Int): String
    fun outputLabel(deviceId: Int): String
    fun isPrivateOutput(deviceId: Int): Boolean
}
