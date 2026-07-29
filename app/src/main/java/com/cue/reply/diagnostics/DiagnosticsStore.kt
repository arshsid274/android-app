package com.cue.reply.diagnostics

import com.cue.reply.domain.model.ServiceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

enum class SelectorHealth { UNKNOWN, OK, BROKEN }

data class ServiceDiagnostics(
    val health: SelectorHealth = SelectorHealth.UNKNOWN,
    val lastSuccessTs: Long? = null,
    val lastBrokenTs: Long? = null,
    val a11yDetected: Boolean = false,
)

/**
 * Live health of the scrape path, surfaced on the Diagnostics screen (F7). When a
 * dating app updates and the SelectorMap goes stale, that shows up here as BROKEN.
 */
class DiagnosticsStore(private val clock: () -> Long = { System.currentTimeMillis() }) {

    private val _state = MutableStateFlow<Map<ServiceType, ServiceDiagnostics>>(emptyMap())
    val state: StateFlow<Map<ServiceType, ServiceDiagnostics>> = _state

    fun markHealthy(service: ServiceType) = _state.update { m ->
        m + (service to (m[service] ?: ServiceDiagnostics()).copy(
            health = SelectorHealth.OK,
            lastSuccessTs = clock(),
        ))
    }

    fun markBroken(service: ServiceType) = _state.update { m ->
        m + (service to (m[service] ?: ServiceDiagnostics()).copy(
            health = SelectorHealth.BROKEN,
            lastBrokenTs = clock(),
        ))
    }

    fun markDetection(service: ServiceType, detected: Boolean) = _state.update { m ->
        m + (service to (m[service] ?: ServiceDiagnostics()).copy(a11yDetected = detected))
    }

    fun forService(service: ServiceType): ServiceDiagnostics =
        _state.value[service] ?: ServiceDiagnostics()
}
