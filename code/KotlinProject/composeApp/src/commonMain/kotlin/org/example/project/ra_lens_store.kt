package org.example.project

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * In-memory merged RA Lens analysis plus asynchronous persistence to [ra_lens_sessions].
 */
object RaLensStore {
    private val byJoint: MutableMap<String, JointRomScoreApi> = linkedMapOf()
    private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    var latestRomAnalysis: RaLensAnalyzeResponseApi? = null
        private set

    /** Prior merged ROM burden before the latest [mergeAnalysis]; used for trending narratives. */
    var previousOverallRomBurden: Double? = null
        private set

    fun mergeAnalysis(
        newAnalysis: RaLensAnalyzeResponseApi?,
        captureMode: String = "unknown",
        sessionJointLabel: String? = null,
        sessionSide: String? = null
    ) {
        if (newAnalysis == null) return
        val uid = AuthService.getCurrentUser()?.id
        latestRomAnalysis?.let { previousOverallRomBurden = it.overallRomBurden }
        newAnalysis.jointScores.forEach { row ->
            byJoint[row.joint] = row
        }
        val merged = byJoint.values.sortedByDescending { it.score }
        val top = merged.firstOrNull()
        val burden = if (merged.isEmpty()) 0.0 else merged.map { it.score }.average()
        latestRomAnalysis = RaLensAnalyzeResponseApi(
            mostAffectedJoint = top,
            overallRomBurden = burden,
            jointScores = merged,
            availableScripts = newAnalysis.availableScripts
        )

        val jointLabel =
            newAnalysis.mostAffectedJoint?.joint
                ?: newAnalysis.jointScores.firstOrNull()?.joint
                ?: "unknown"
        persistScope.launch {
            runCatching {
                RaLensDebugLogger.appendSessionAfterBackendResult(
                    rawBackendResponse = newAnalysis,
                    captureMode = captureMode,
                    sessionJointLabel = sessionJointLabel,
                    sessionSide = sessionSide
                )
            }.exceptionOrNull()?.let { t ->
                raLensDebugLog("RaLensDebug", "session debug log failed: ${t.message}")
            }
            if (uid != null) {
                runCatching {
                    RaLensSessionRepository.insertSession(
                        userId = uid,
                        joint = jointLabel,
                        side = "left",
                        analysis = newAnalysis,
                        captureMode = captureMode
                    )
                }
            }
        }
    }

    /** Replace merged state from a hydrated session row (no persistence round-trip). */
    fun replaceWithHydratedAnalysis(analysis: RaLensAnalyzeResponseApi) {
        byJoint.clear()
        analysis.jointScores.forEach { row -> byJoint[row.joint] = row }
        val merged = byJoint.values.sortedByDescending { it.score }
        val top = merged.firstOrNull()
        val burden = if (merged.isEmpty()) 0.0 else merged.map { it.score }.average()
        latestRomAnalysis = RaLensAnalyzeResponseApi(
            mostAffectedJoint = top,
            overallRomBurden = burden,
            jointScores = merged,
            availableScripts = analysis.availableScripts
        )
        previousOverallRomBurden = null
    }
}
