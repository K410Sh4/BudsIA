package com.k410sh4.budsia.core.ai.models

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

class VerifiedModelManager(
    context: Context
) : ModelManager {

    private val modelRoot = File(context.filesDir, "ai-models")
    private val mutex = Mutex()

    private val _statuses = MutableStateFlow(
        AiModelCatalog.all.associate { descriptor ->
            descriptor.id to ModelInstallStatus(
                descriptor = descriptor,
                state = ModelInstallState.NOT_INSTALLED
            )
        }
    )
    override val statuses: StateFlow<Map<String, ModelInstallStatus>> =
        _statuses.asStateFlow()

    override suspend fun refresh() {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                modelRoot.mkdirs()

                val refreshed = AiModelCatalog.all.associate { descriptor ->
                    val file = installedFile(descriptor)
                    val installed = file.isFile &&
                        verifyFile(file, descriptor)

                    descriptor.id to ModelInstallStatus(
                        descriptor = descriptor,
                        state = if (installed) {
                            ModelInstallState.INSTALLED
                        } else {
                            ModelInstallState.NOT_INSTALLED
                        },
                        bytesDownloaded = if (installed) descriptor.sizeBytes else 0L
                    )
                }

                _statuses.value = refreshed
            }
        }
    }

    override suspend fun download(modelId: String): Result<File> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val descriptor = AiModelCatalog.byId(modelId)
                    ?: return@withLock Result.failure(
                        IllegalArgumentException("Unknown AI model: $modelId")
                    )

                modelRoot.mkdirs()
                val finalFile = installedFile(descriptor)

                if (finalFile.isFile && verifyFile(finalFile, descriptor)) {
                    publish(
                        descriptor = descriptor,
                        state = ModelInstallState.INSTALLED,
                        bytesDownloaded = descriptor.sizeBytes
                    )
                    return@withLock Result.success(finalFile)
                }

                val partialFile = File(
                    modelRoot,
                    "${descriptor.fileName}.part"
                )
                partialFile.delete()

                publish(
                    descriptor = descriptor,
                    state = ModelInstallState.DOWNLOADING,
                    bytesDownloaded = 0L
                )

                var connection: HttpURLConnection? = null
                try {
                    connection = (URL(descriptor.downloadUrl)
                        .openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15_000
                        readTimeout = 30_000
                        instanceFollowRedirects = true
                        requestMethod = "GET"
                        useCaches = false
                        setRequestProperty(
                            "User-Agent",
                            "BudsIA-ModelManager/3"
                        )
                    }

                    val statusCode = connection.responseCode
                    check(statusCode in 200..299) {
                        "Model download failed with HTTP $statusCode."
                    }

                    val digest = MessageDigest.getInstance("SHA-256")
                    var downloaded = 0L
                    var nextProgressPublish = 256L * 1024L

                    BufferedInputStream(connection.inputStream, 128 * 1024)
                        .use { input ->
                            FileOutputStream(partialFile).use { output ->
                                val buffer = ByteArray(128 * 1024)
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    if (count == 0) continue

                                    output.write(buffer, 0, count)
                                    digest.update(buffer, 0, count)
                                    downloaded += count

                                    check(downloaded <= descriptor.sizeBytes) {
                                        "Downloaded model exceeds pinned size."
                                    }

                                    if (downloaded >= nextProgressPublish) {
                                        publish(
                                            descriptor = descriptor,
                                            state = ModelInstallState.DOWNLOADING,
                                            bytesDownloaded = downloaded
                                        )
                                        nextProgressPublish =
                                            downloaded + 256L * 1024L
                                    }
                                }
                                output.fd.sync()
                            }
                        }

                    publish(
                        descriptor = descriptor,
                        state = ModelInstallState.VERIFYING,
                        bytesDownloaded = downloaded
                    )

                    check(downloaded == descriptor.sizeBytes) {
                        "Model size mismatch: expected ${descriptor.sizeBytes}, got $downloaded."
                    }

                    val actualSha = digest.digest().toHex()
                    check(
                        actualSha.equals(
                            descriptor.sha256,
                            ignoreCase = true
                        )
                    ) {
                        "Model SHA-256 mismatch."
                    }

                    atomicReplace(partialFile, finalFile)

                    check(verifyFile(finalFile, descriptor)) {
                        "Post-install model verification failed."
                    }

                    publish(
                        descriptor = descriptor,
                        state = ModelInstallState.INSTALLED,
                        bytesDownloaded = descriptor.sizeBytes
                    )

                    Result.success(finalFile)
                } catch (cancelled: CancellationException) {
                    partialFile.delete()
                    publish(
                        descriptor = descriptor,
                        state = ModelInstallState.NOT_INSTALLED,
                        bytesDownloaded = 0L
                    )
                    throw cancelled
                } catch (error: Throwable) {
                    partialFile.delete()
                    publish(
                        descriptor = descriptor,
                        state = ModelInstallState.ERROR,
                        bytesDownloaded = 0L,
                        errorMessage = error.message
                            ?: error::class.java.simpleName
                    )
                    Result.failure(error)
                } finally {
                    connection?.disconnect()
                }
            }
        }

    override suspend fun remove(modelId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val descriptor = AiModelCatalog.byId(modelId)
                    ?: return@withLock Result.failure(
                        IllegalArgumentException("Unknown AI model: $modelId")
                    )

                return@withLock runCatching {
                    val finalFile = installedFile(descriptor)
                    val partialFile = File(
                        modelRoot,
                        "${descriptor.fileName}.part"
                    )

                    if (finalFile.exists() && !finalFile.delete()) {
                        error("Unable to remove installed model.")
                    }
                    partialFile.delete()

                    publish(
                        descriptor = descriptor,
                        state = ModelInstallState.NOT_INSTALLED,
                        bytesDownloaded = 0L
                    )
                }
            }
        }

    override suspend fun verifiedFile(modelId: String): File? =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val descriptor = AiModelCatalog.byId(modelId)
                    ?: return@withLock null

                val file = installedFile(descriptor)
                if (file.isFile && verifyFile(file, descriptor)) {
                    publish(
                        descriptor = descriptor,
                        state = ModelInstallState.INSTALLED,
                        bytesDownloaded = descriptor.sizeBytes
                    )
                    file
                } else {
                    if (file.exists()) file.delete()
                    publish(
                        descriptor = descriptor,
                        state = ModelInstallState.NOT_INSTALLED,
                        bytesDownloaded = 0L
                    )
                    null
                }
            }
        }

    private fun installedFile(descriptor: AiModelDescriptor): File =
        File(modelRoot, descriptor.fileName)

    private fun verifyFile(
        file: File,
        descriptor: AiModelDescriptor
    ): Boolean {
        if (!file.isFile || file.length() != descriptor.sizeBytes) {
            return false
        }

        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(128 * 1024).use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }

        return digest.digest()
            .toHex()
            .equals(descriptor.sha256, ignoreCase = true)
    }

    private fun atomicReplace(source: File, destination: File) {
        destination.parentFile?.mkdirs()
        runCatching {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        }.getOrElse {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }

    private fun publish(
        descriptor: AiModelDescriptor,
        state: ModelInstallState,
        bytesDownloaded: Long,
        errorMessage: String? = null
    ) {
        _statuses.value = _statuses.value.toMutableMap().apply {
            put(
                descriptor.id,
                ModelInstallStatus(
                    descriptor = descriptor,
                    state = state,
                    bytesDownloaded = bytesDownloaded,
                    totalBytes = descriptor.sizeBytes,
                    errorMessage = errorMessage
                )
            )
        }
    }

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
}
