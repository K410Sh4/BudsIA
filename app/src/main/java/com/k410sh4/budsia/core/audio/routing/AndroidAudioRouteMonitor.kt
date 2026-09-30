package com.k410sh4.budsia.core.audio.routing

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidAudioRouteMonitor(
    context: Context
) : AudioRouteMonitor {

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _devices = MutableStateFlow(readCatalog())
    override val devices: StateFlow<AudioDeviceCatalog> = _devices.asStateFlow()

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            refresh()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            refresh()
        }
    }

    init {
        audioManager.registerAudioDeviceCallback(
            callback,
            Handler(Looper.getMainLooper())
        )
    }

    override fun inputLabel(deviceId: Int): String =
        devices.value.inputs
            .firstOrNull { it.id == deviceId }
            ?.let { "${it.productName} • ${it.typeLabel}" }
            ?: if (deviceId == 0) "Entrada padrão do sistema" else "Entrada #$deviceId"

    override fun outputLabel(deviceId: Int): String =
        devices.value.outputs
            .firstOrNull { it.id == deviceId }
            ?.let { "${it.productName} • ${it.typeLabel}" }
            ?: if (deviceId == 0) "Saída padrão do sistema" else "Saída #$deviceId"

    override fun isPrivateOutput(deviceId: Int): Boolean {
        val type = devices.value.outputs
            .firstOrNull { it.id == deviceId }
            ?.type
            ?: return false

        return type in PRIVATE_OUTPUT_TYPES
    }

    private fun refresh() {
        _devices.value = readCatalog()
    }

    private fun readCatalog(): AudioDeviceCatalog {
        val inputs = audioManager
            .getDevices(AudioManager.GET_DEVICES_INPUTS)
            .map { it.toDescriptor() }
            .sortedBy { it.id }

        val outputs = audioManager
            .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .map { it.toDescriptor() }
            .sortedBy { it.id }

        return AudioDeviceCatalog(
            inputs = inputs,
            outputs = outputs
        )
    }

    private fun AudioDeviceInfo.toDescriptor(): AudioDeviceDescriptor =
        AudioDeviceDescriptor(
            id = id,
            productName = productName
                ?.toString()
                ?.takeIf { it.isNotBlank() }
                ?: fallbackName(type),
            type = type,
            typeLabel = typeLabel(type),
            isInput = isSource,
            isOutput = isSink,
            isBluetooth = type in BLUETOOTH_TYPES,
            isCommunicationCapable = type in COMMUNICATION_TYPES
        )

    private fun fallbackName(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Microfone do telefone"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Alto-falante do telefone"
        else -> "Dispositivo de áudio"
    }

    private fun typeLabel(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "MIC INTERNO"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "SPEAKER"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "HEADSET"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "FONES"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BLUETOOTH HFP/SCO"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "BLUETOOTH A2DP"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB AUDIO"
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB ACCESSORY"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB HEADSET"
        AudioDeviceInfo.TYPE_BLE_HEADSET -> "BLE HEADSET"
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> "BLE SPEAKER"
        else -> "TYPE $type"
    }

    companion object {
        private val BLUETOOTH_TYPES = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER
        )

        private val COMMUNICATION_TYPES = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET
        )

        private val PRIVATE_OUTPUT_TYPES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLE_HEADSET
        )
    }
}
