package com.k410sh4.budsia.core.audio.routing

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat

class AndroidAudioRouteController(
    private val context: Context
) : AudioRouteController {

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var ownsCommunicationMode = false
    private var previousAudioMode: Int? = null

    @Synchronized
    override fun prepare(
        inputDeviceId: Int,
        outputDeviceId: Int
    ): PreparedAudioRoute {
        release()

        if (inputDeviceId <= 0) {
            return PreparedAudioRoute(
                success = true,
                inputDeviceId = inputDeviceId,
                outputDeviceId = outputDeviceId,
                communicationMode = false
            )
        }

        val input = audioManager
            .getDevices(AudioManager.GET_DEVICES_INPUTS)
            .firstOrNull { it.id == inputDeviceId }
            ?: return PreparedAudioRoute(
                success = false,
                inputDeviceId = inputDeviceId,
                outputDeviceId = outputDeviceId,
                communicationMode = false,
                message = "A entrada selecionada não está mais disponível."
            )

        if (!isCommunicationBluetooth(input.type)) {
            return PreparedAudioRoute(
                success = true,
                inputDeviceId = inputDeviceId,
                outputDeviceId = outputDeviceId,
                communicationMode = false
            )
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return PreparedAudioRoute(
                success = false,
                inputDeviceId = inputDeviceId,
                outputDeviceId = outputDeviceId,
                communicationMode = true,
                message = "O roteamento Bluetooth explícito do BudsIA requer Android 12 ou superior."
            )
        }

        if (!hasBluetoothConnectPermission()) {
            return PreparedAudioRoute(
                success = false,
                inputDeviceId = inputDeviceId,
                outputDeviceId = outputDeviceId,
                communicationMode = true,
                message = "Permita acesso a dispositivos próximos para usar o microfone Bluetooth."
            )
        }

        val communicationDevice =
            audioManager.availableCommunicationDevices
                .firstOrNull { it.id == input.id }
                ?: audioManager.availableCommunicationDevices
                    .firstOrNull {
                        it.type == input.type &&
                            it.productName.toString() ==
                                input.productName.toString()
                    }
                ?: return PreparedAudioRoute(
                    success = false,
                    inputDeviceId = inputDeviceId,
                    outputDeviceId = outputDeviceId,
                    communicationMode = true,
                    message = "O dispositivo Bluetooth não está disponível como rota de comunicação."
                )

        previousAudioMode = audioManager.mode
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        ownsCommunicationMode = true

        if (!audioManager.setCommunicationDevice(communicationDevice)) {
            release()
            return PreparedAudioRoute(
                success = false,
                inputDeviceId = inputDeviceId,
                outputDeviceId = outputDeviceId,
                communicationMode = true,
                message = "O Android recusou a rota de comunicação Bluetooth."
            )
        }

        return PreparedAudioRoute(
            success = true,
            inputDeviceId = inputDeviceId,
            // Android communication routing owns the paired input/output path.
            // Leaving Oboe output on the system route avoids fighting the HFP/
            // BLE communication device with a simultaneous A2DP preference.
            outputDeviceId = 0,
            communicationMode = true,
            communicationDeviceId = communicationDevice.id
        )
    }

    @Synchronized
    override fun release() {
        if (!ownsCommunicationMode) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                audioManager.clearCommunicationDevice()
            }
        }

        val restoreMode = previousAudioMode
            ?: AudioManager.MODE_NORMAL

        runCatching {
            audioManager.mode = restoreMode
        }
        previousAudioMode = null
        ownsCommunicationMode = false
    }

    private fun hasBluetoothConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

    private fun isCommunicationBluetooth(type: Int): Boolean =
        type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            type == AudioDeviceInfo.TYPE_BLE_HEADSET
}
