package org.example.project

/**
 * Pure derivation helpers for longitudinal interpreted ROM (no I/O).
 * Keeps repository thin and reusable when the session source becomes remote.
 */
object RomLongitudinalAnalytics {

    private val sessionDayRegex = Regex("""ralens-demo-d(\d+)-""")

    fun parseDayIndex(sessionId: String): Int? =
        sessionDayRegex.find(sessionId)?.groupValues?.getOrNull(1)?.toIntOrNull()

    /**
     * Sorted timeline: [dayIndex, originalIndex, session].
     */
    fun indexSessionsByDay(sessions: List<InterpretedRaLensSession>): List<Triple<Int, Int, InterpretedRaLensSession>> =
        sessions.mapIndexed { idx, session ->
            val parsed = parseDayIndex(session.sessionId) ?: idx
            Triple(parsed, idx, session)
        }.sortedBy { it.first }

    fun romScoreForJoint(session: InterpretedRaLensSession, jointKey: String): Int? =
        session.joints.find { it.jointName == jointKey }?.romScore

    fun weakestJointRomScore(session: InterpretedRaLensSession): Int =
        romScoreForJoint(session, session.weakestJoint) ?: session.overallRomScore

    fun mapRomTrendPoints(indexed: List<Triple<Int, Int, InterpretedRaLensSession>>): List<RomTrendPoint> =
        indexed.map { (dayIndex, _, session) ->
            RomTrendPoint(dayIndex, session.overallRomScore, session.timestamp)
        }

    fun mapWeakestJointHistory(indexed: List<Triple<Int, Int, InterpretedRaLensSession>>): List<WeakestJointPoint> =
        indexed.map { (dayIndex, _, session) ->
            val score = romScoreForJoint(session, session.weakestJoint) ?: session.overallRomScore
            WeakestJointPoint(dayIndex, session.weakestJoint, score)
        }

    fun mapSeverityProgression(indexed: List<Triple<Int, Int, InterpretedRaLensSession>>): List<SeverityDistribution> =
        indexed.map { (dayIndex, _, session) ->
            SeverityDistribution(
                dayIndex = dayIndex,
                normal = session.joints.count { it.severity == RomInterpretSeverity.NORMAL },
                mild = session.joints.count { it.severity == RomInterpretSeverity.MILD },
                moderate = session.joints.count { it.severity == RomInterpretSeverity.MODERATE },
                severe = session.joints.count { it.severity == RomInterpretSeverity.SEVERE }
            )
        }

    private fun elevatedConcernCount(session: InterpretedRaLensSession): Int =
        session.joints.count {
            it.severity == RomInterpretSeverity.MODERATE || it.severity == RomInterpretSeverity.SEVERE
        }

    /**
     * Flags day transitions where composite ROM worsens materially:
     * — overall score drops sharply vs prior day,
     * — moderate/severe joint counts spike,
     * — ROM score for [InterpretedRaLensSession.weakestJoint] drops sharply vs prior day for that joint label.
     */
    fun detectFlareMoments(indexedSessions: List<Triple<Int, Int, InterpretedRaLensSession>>): List<FlareMoment> {
        if (indexedSessions.size < 2) return emptyList()
        val out = mutableListOf<FlareMoment>()
        for (i in 1 until indexedSessions.size) {
            val (dayPrev, _, prev) = indexedSessions[i - 1]
            val (dayCurr, _, curr) = indexedSessions[i]
            if (dayCurr <= dayPrev) continue

            val overallDrop = prev.overallRomScore - curr.overallRomScore
            val prevElev = elevatedConcernCount(prev)
            val currElev = elevatedConcernCount(curr)
            val severityDelta = currElev - prevElev

            val jointKey = curr.weakestJoint
            val prevWeakScore = romScoreForJoint(prev, jointKey) ?: prev.overallRomScore
            val currWeakScore = romScoreForJoint(curr, jointKey) ?: curr.overallRomScore
            val weakestJointDrop = prevWeakScore - currWeakScore

            val sharpOverall = overallDrop >= 6
            val severitySpike = severityDelta >= 3 || (currElev >= 4 && severityDelta >= 2)
            val weakestSpike = weakestJointDrop >= 8

            if (!sharpOverall && !severitySpike && !weakestSpike) continue

            val reasons = buildList {
                if (sharpOverall) add("Overall ROM dropped ${overallDrop.coerceAtLeast(0)} pts vs prior session.")
                if (severitySpike) {
                    add(
                        "Moderate/severe joint count rose by $severityDelta (now $currElev)."
                    )
                }
                if (weakestSpike) {
                    add(
                        "Weakest joint (${jointKey}) ROM fell by ${weakestJointDrop.coerceAtLeast(0)} pts vs prior session."
                    )
                }
            }

            val scoreDropCandidates = buildList {
                if (sharpOverall) add(overallDrop.coerceAtLeast(0))
                if (weakestSpike) add(weakestJointDrop.coerceAtLeast(0))
                if (severitySpike) add(maxOf(overallDrop, severityDelta))
            }
            val scoreDrop = scoreDropCandidates.maxOrNull()?.coerceAtLeast(0) ?: 0

            out.add(
                FlareMoment(
                    dayIndex = dayCurr,
                    reason = reasons.joinToString(" "),
                    scoreDrop = scoreDrop
                )
            )
        }
        return out
    }

    fun buildDashboardState(sessions: List<InterpretedRaLensSession>): RomDashboardLongitudinalState {
        if (sessions.isEmpty()) {
            return RomDashboardLongitudinalState(
                allSessions = emptyList(),
                latestSession = null,
                romTrendPoints = emptyList(),
                weakestJointHistory = emptyList(),
                severityProgression = emptyList(),
                flareDetectionMoments = emptyList()
            )
        }

        val indexed = indexSessionsByDay(sessions)
        val ordered = indexed.map { it.third }

        return RomDashboardLongitudinalState(
            allSessions = ordered,
            latestSession = ordered.lastOrNull(),
            romTrendPoints = mapRomTrendPoints(indexed),
            weakestJointHistory = mapWeakestJointHistory(indexed),
            severityProgression = mapSeverityProgression(indexed),
            flareDetectionMoments = detectFlareMoments(indexed)
        )
    }
}
