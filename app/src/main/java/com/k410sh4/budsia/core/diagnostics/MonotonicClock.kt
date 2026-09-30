package com.k410sh4.budsia.core.diagnostics

import android.os.SystemClock

interface MonotonicClock {
    fun nowNanos(): Long
}

class AndroidMonotonicClock : MonotonicClock {
    override fun nowNanos(): Long = SystemClock.elapsedRealtimeNanos()
}
