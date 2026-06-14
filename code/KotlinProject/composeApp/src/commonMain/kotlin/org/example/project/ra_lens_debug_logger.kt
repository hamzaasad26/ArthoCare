package org.example.project

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Calibration-only sink: appends RA Lens sessions to [RA_LENS_DEBUG_FILENAME].
 * Does not touch ML, UI layout, or introduce new Supabase writes.
 */
object RaLensDebugLogger {

    private const val TAG = "RaLensDebug"

    private val mutex = Mutex()
    private var auditEmitted = false

    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun logRomPipelineAuditOnce() {
        mutex.withLock {
            emitAuditLocked()
        }
    }

    suspend fun appendSessionAfterBackendResult(
        rawBackendResponse: RaLensAnalyzeResponseApi,
        captureMode: String,
        sessionJointLabel: String?,
        sessionSide: String?
    ) {
        mutex.withLock {
            emitAuditLocked()
            val jointKey = sessionJointLabel?.takeIf { it.isNotBlank() }?.lowercase()
                ?: rawBackendResponse.jointScores.firstOrNull()?.joint?.lowercase()
                ?: rawBackendResponse.mostAffectedJoint?.joint?.lowercase()
                ?: "unknown"
            val sideKey = sessionSide?.takeIf { it.isNotBlank() }?.lowercase() ?: "left"
            val primary =
                rawBackendResponse.mostAffectedJoint ?: rawBackendResponse.jointScores.firstOrNull()
            val sessionId = newRaLensSessionId()
            val ts = Clock.System.now().toString()

            val romDetected = buildJsonObject {
                put(
                    "raw_value",
                    primary?.deficitDeg?.let { JsonPrimitive(it) } ?: JsonNull
                )
                put(
                    "angle_value",
                    primary?.measuredRomDeg?.let { JsonPrimitive(it) } ?: JsonNull
                )
                put(
                    "normalized_value",
                    primary?.deficitPct?.let { JsonPrimitive(it) } ?: JsonNull
                )
            }

            val uiRomDisplay = buildString {
                append("${rawBackendResponse.overallRomBurden.toInt()}% overall ROM burden (ResultsScene)")
                primary?.let {
                    append("; ")
                    append("${it.joint}: ${it.deficitPct.toInt()}% deficit (${it.status})")
                }
            }
            val uiComputed = buildJsonObject {
                put("rom_display_value", JsonPrimitive(uiRomDisplay))
            }

            val record = buildJsonObject {
                put("session_id", JsonPrimitive(sessionId))
                put("timestamp", JsonPrimitive(ts))
                put("joint", JsonPrimitive(jointKey))
                put("side", JsonPrimitive(sideKey))
                put("capture_mode", JsonPrimitive(captureMode))
                put(
                    "raw_backend_response",
                    json.encodeToJsonElement(RaLensAnalyzeResponseApi.serializer(), rawBackendResponse)
                )
                put("rom_fields_detected", romDetected)
                put("ui_computed_values", uiComputed)
            }

            appendRecordToJsonFile(record)

            logSessionToLines(sessionId, ts, jointKey, sideKey, captureMode, rawBackendResponse, primary)
        }
    }

    private fun emitAuditLocked() {
        if (auditEmitted) return
        auditEmitted = true
        RaLensRomAudit.lines.forEach { raLensDebugLog(TAG, it) }
    }

    private suspend fun appendRecordToJsonFile(record: JsonObject) {
        val existingBytes = readRaLensDebugJsonBytes()
        val existingText = existingBytes?.decodeToString()?.trim().orEmpty()
        val elements = mutableListOf<JsonElement>()
        when {
            existingText.isEmpty() -> { /* new file */ }
            else -> runCatching {
                when (val root = json.parseToJsonElement(existingText)) {
                    is JsonArray -> elements.addAll(root)
                    else -> elements.add(root)
                }
            }
        }
        elements.add(record)
        val array = JsonArray(elements)
        writeRaLensDebugJsonBytes(json.encodeToString(JsonArray.serializer(), array).encodeToByteArray())
        raLensDebugLog(TAG, "Appended session to $RA_LENS_DEBUG_FILENAME (${elements.size} total entries)")
    }

    private fun logSessionToLines(
        sessionId: String,
        timestampIso: String,
        joint: String,
        side: String,
        captureMode: String,
        raw: RaLensAnalyzeResponseApi,
        primary: JointRomScoreApi?
    ) {
        raLensDebugLog(TAG, "========== RA Lens session $sessionId ==========")
        raLensDebugLog(TAG, "timestamp=$timestampIso joint=$joint side=$side capture_mode=$captureMode")
        json.encodeToString(RaLensAnalyzeResponseApi.serializer(), raw).chunked(3500).forEachIndexed { i, part ->
            raLensDebugLog(TAG, "raw_backend_response.part${i + 1}=$part")
        }
        primary?.let {
            raLensDebugLog(
                TAG,
                "rom_extract joint=${it.joint} measured_rom_deg=${it.measuredRomDeg} baseline_rom_deg=${it.baselineRomDeg} deficit_deg=${it.deficitDeg} deficit_pct=${it.deficitPct} score=${it.score} status=${it.status}"
            )
        } ?: raLensDebugLog(TAG, "rom_extract=no primary joint row")
        raLensDebugLog(TAG, "ui_surface overall_rom_burden_display=${raw.overallRomBurden.toInt()}%")
        raLensDebugLog(TAG, "========== end session $sessionId ==========")
    }
}

private object RaLensRomAudit {
    val lines: List<String> = listOf(
        "[ROM_AUDIT] === RA Lens ROM data pipeline (client codebase scan) ===",
        "[ROM_AUDIT] (1) Where ROM is computed: Primary numeric ROM signals come from FastAPI ML backend as JSON deserialized into RaLensAnalyzeResponseApi / JointRomScoreApi (ml_api_contracts.kt). The Kotlin client does not run pose/ROM ML; it merges multiple captures in RaLensStore.mergeAnalysis by averaging JointRomScoreApi.score across joints for overall_rom_burden display state.",
        "[ROM_AUDIT] (2) Backend-facing fields (Kotlin models): joint; measured_rom_deg; baseline_rom_deg; deficit_deg; deficit_pct; status; score; plus response-level overall_rom_burden; most_affected_joint; joint_scores[]; available_scripts[]. Endpoint wrappers: POST /ralens/analyze-media (MlApiService.analyzeRaLensMedia); POST /ralens/analyze-rom exists but is unused by UI; desktop polling uses RaLensDesktopStatusResponseApi.analysis.",
        "[ROM_AUDIT] (3) Persistence: In-memory merged snapshot RaLensStore.latestRomAnalysis / previousOverallRomBurden (ra_lens_store.kt). Supabase ra_lens_sessions via RaLensSessionRepository.insertSession stores embedded analysis JSON and rom_score (health timeline path). ROM is not written to prediction_snapshots (overall predictions only). UI reads RaLensStore / hydrated timeline—not recomputed from disk locally.",
        "[ROM_AUDIT] (4) Normalization: No dedicated clinical ROM normalization layer in Kotlin beyond backend-supplied deficit_deg/deficit_pct/score and client-side merge averaging of score values across joints. measured_rom_deg vs baseline_rom_deg are surfaced as-is from API.",
        "[ROM_AUDIT] Desktop Compose JVM target is not present in composeApp; debug JSON uses Android filesDir and iOS Documents. JVM desktop would use a project-root debug path when that target is added.",
        "[ROM_AUDIT] === End ROM audit ==="
    )
}
