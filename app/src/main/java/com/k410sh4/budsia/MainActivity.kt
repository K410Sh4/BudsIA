package com.k410sh4.budsia

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.k410sh4.budsia.feature.live.AudioFocusRoute
import com.k410sh4.budsia.ui.theme.BudsIATheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BudsIATheme {
                var hasMicPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }

                var hasBluetoothPermission by remember {
                    mutableStateOf(hasBluetoothConnectPermission())
                }

                val micPermissionLauncher =
                    rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { granted ->
                        hasMicPermission = granted
                    }

                val bluetoothPermissionLauncher =
                    rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { granted ->
                        hasBluetoothPermission = granted
                    }

                AudioFocusRoute(
                    hasMicrophonePermission = hasMicPermission,
                    hasBluetoothConnectPermission =
                        hasBluetoothPermission,
                    onRequestMicrophonePermission = {
                        micPermissionLauncher.launch(
                            Manifest.permission.RECORD_AUDIO
                        )
                    },
                    onRequestBluetoothPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            bluetoothPermissionLauncher.launch(
                                Manifest.permission.BLUETOOTH_CONNECT
                            )
                        } else {
                            hasBluetoothPermission = true
                        }
                    }
                )
            }
        }
    }

    private fun hasBluetoothConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
}
