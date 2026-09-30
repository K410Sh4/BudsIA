package com.k410sh4.budsia.core.time

interface WallClock {
    fun nowEpochMillis(): Long
}

class SystemWallClock : WallClock {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()
}
