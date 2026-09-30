package com.k410sh4.budsia.core.performance

import android.content.Context
import android.os.Build
import android.os.PerformanceHintManager
import android.os.Process

class AndroidInferencePerformanceHintFactory(
    private val context: Context
) : InferencePerformanceHintFactory {

    override fun open(
        targetWorkDurationNanos: Long
    ): InferencePerformanceHintSession {
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            targetWorkDurationNanos <= 0L
        ) {
            return NoOpInferencePerformanceHintSession
        }

        val manager = context.getSystemService(
            PerformanceHintManager::class.java
        ) ?: return NoOpInferencePerformanceHintSession

        val session = runCatching {
            manager.createHintSession(
                intArrayOf(Process.myTid()),
                targetWorkDurationNanos
            )
        }.getOrNull()
            ?: return NoOpInferencePerformanceHintSession

        return AndroidInferencePerformanceHintSession(session)
    }
}

private class AndroidInferencePerformanceHintSession(
    private val session: PerformanceHintManager.Session
) : InferencePerformanceHintSession {

    override val supported: Boolean = true

    override fun reportActualWorkDuration(
        actualDurationNanos: Long
    ) {
        if (actualDurationNanos <= 0L) return

        runCatching {
            session.reportActualWorkDuration(
                actualDurationNanos
            )
        }
    }

    override fun setPreferPowerEfficiency(
        enabled: Boolean
    ) {
        if (Build.VERSION.SDK_INT < 35) return

        runCatching {
            session.setPreferPowerEfficiency(enabled)
        }
    }

    override fun close() {
        runCatching {
            session.close()
        }
    }
}
