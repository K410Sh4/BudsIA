package com.k410sh4.budsia.core.performance

import kotlinx.coroutines.flow.StateFlow

interface AiPerformanceMonitor {
    val snapshot: StateFlow<DevicePerformanceSnapshot>
}
