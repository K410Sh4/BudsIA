package com.k410sh4.budsia.core.ai.models

import kotlinx.coroutines.flow.StateFlow
import java.io.File

enum class ModelInstallState {
    NOT_INSTALLED,
    DOWNLOADING,
    VERIFYING,
    INSTALLED,
    ERROR
}

data class ModelInstallStatus(
    val descriptor: AiModelDescriptor,
    val state: ModelInstallState,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = descriptor.sizeBytes,
    val errorMessage: String? = null
) {
    val progress: Float
        get() = if (totalBytes <= 0L) 0f
        else (bytesDownloaded.toDouble() / totalBytes.toDouble())
            .coerceIn(0.0, 1.0)
            .toFloat()
}

interface ModelManager {
    val statuses: StateFlow<Map<String, ModelInstallStatus>>

    suspend fun refresh()
    suspend fun download(modelId: String): Result<File>
    suspend fun remove(modelId: String): Result<Unit>
    suspend fun verifiedFile(modelId: String): File?
}
