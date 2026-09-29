package com.k410sh4.budsia.core.language

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.IdentifiedLanguage
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.k410sh4.budsia.domain.model.LanguageGuess
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalLanguageEngine @Inject constructor() {
    private val identifier = LanguageIdentification.getClient()
    private val modelManager = RemoteModelManager.getInstance()

    suspend fun identify(text: String): String? =
        identifyWithConfidence(text).languageTag

    suspend fun identifyWithConfidence(
        text: String,
        minimumConfidence: Float = 0.55f
    ): LanguageGuess {
        if (text.isBlank()) return LanguageGuess(null, 0f)

        val candidates = awaitTask<List<IdentifiedLanguage>> { ok, fail ->
            identifier.identifyPossibleLanguages(text)
                .addOnSuccessListener(ok)
                .addOnFailureListener(fail)
        }

        val best = candidates
            .filter { it.languageTag != "und" }
            .maxByOrNull { it.confidence }

        return if (best == null || best.confidence < minimumConfidence) {
            LanguageGuess(null, best?.confidence ?: 0f)
        } else {
            LanguageGuess(best.languageTag, best.confidence)
        }
    }

    suspend fun downloadedLanguages(): Set<String> {
        val models = awaitTask<Set<TranslateRemoteModel>> { ok, fail ->
            modelManager
                .getDownloadedModels(TranslateRemoteModel::class.java)
                .addOnSuccessListener(ok)
                .addOnFailureListener(fail)
        }
        return models.map { it.language }.toSet()
    }

    suspend fun isLanguageModelDownloaded(languageTag: String): Boolean {
        val language = TranslateLanguage.fromLanguageTag(languageTag) ?: return false
        return language in downloadedLanguages()
    }

    suspend fun downloadLanguageModel(languageTag: String): Result<Unit> {
        val language = TranslateLanguage.fromLanguageTag(languageTag)
            ?: return Result.failure(IllegalArgumentException("Unsupported language: $languageTag"))

        val model = TranslateRemoteModel.Builder(language).build()
        val conditions = DownloadConditions.Builder().requireWifi().build()

        return runCatching {
            awaitTask<Void> { ok, fail ->
                modelManager.download(model, conditions)
                    .addOnSuccessListener(ok)
                    .addOnFailureListener(fail)
            }
            Unit
        }
    }

    suspend fun translate(
        text: String,
        sourceTag: String,
        targetTag: String,
        allowModelDownload: Boolean
    ): Result<String> {
        if (text.isBlank()) return Result.success("")
        if (sourceTag.equals(targetTag, ignoreCase = true)) return Result.success(text)

        val source = TranslateLanguage.fromLanguageTag(sourceTag)
            ?: return Result.failure(IllegalArgumentException("Unsupported source language: $sourceTag"))
        val target = TranslateLanguage.fromLanguageTag(targetTag)
            ?: return Result.failure(IllegalArgumentException("Unsupported target language: $targetTag"))

        val downloaded = downloadedLanguages()
        if (!allowModelDownload && (source !in downloaded || target !in downloaded)) {
            return Result.failure(
                IllegalStateException("Translation model not installed for $sourceTag → $targetTag")
            )
        }

        val translator = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(source)
                .setTargetLanguage(target)
                .build()
        )

        return try {
            if (allowModelDownload) {
                val conditions = DownloadConditions.Builder().requireWifi().build()
                awaitTask<Void> { ok, fail ->
                    translator.downloadModelIfNeeded(conditions)
                        .addOnSuccessListener(ok)
                        .addOnFailureListener(fail)
                }
            }

            val translated = awaitTask<String> { ok, fail ->
                translator.translate(text)
                    .addOnSuccessListener(ok)
                    .addOnFailureListener(fail)
            }
            Result.success(translated)
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            translator.close()
        }
    }

    private suspend fun <T> awaitTask(
        starter: ((T) -> Unit, (Exception) -> Unit) -> Unit
    ): T = suspendCancellableCoroutine { continuation ->
        starter(
            { value -> if (continuation.isActive) continuation.resume(value) },
            { error -> if (continuation.isActive) continuation.resumeWithException(error) }
        )
    }
}
