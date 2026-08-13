package codekr.api.contest.admin

import codekr.api.contest.entity.Contest
import codekr.api.contest.entity.ContestPhase
import codekr.api.contest.entity.ContestStatus
import java.time.Instant

/** 어드민 대회 상세 (#61). 참가자에게 감춰지는 것도 전부 보인다. */
data class AdminContestResponse(
    val id: Long,
    val slug: String,
    val title: String,
    val description: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val freezeMinutes: Int,
    val submissionCooldownSeconds: Int,
    val freezeAt: Instant?,
    val registrationOpenDuring: Boolean,
    val status: ContestStatus,
    /** 공개 범위 (#465). `status` 와 다른 값이다. */
    val visibility: codekr.api.contest.entity.ContestVisibility,
    val phase: ContestPhase,
    val phaseLabel: String,
    /** 참가자에게 순위가 감춰지는 중인가. 어드민은 그래도 실제 순위를 본다 (#86). */
    val frozen: Boolean,
    val unfrozenAt: Instant?,
    val participantCount: Int,
    val problems: List<AdminContestProblemResponse>,
) {
    companion object {
        fun of(
            contest: Contest,
            now: Instant,
            participantCount: Int,
            problems: List<AdminContestProblemResponse>,
        ): AdminContestResponse {
            val phase = contest.phaseAt(now)
            return AdminContestResponse(
                id = contest.id,
                slug = contest.slug,
                title = contest.title,
                description = contest.description,
                startsAt = contest.startsAt,
                endsAt = contest.endsAt,
                freezeMinutes = contest.freezeMinutes,
                submissionCooldownSeconds = contest.submissionCooldownSeconds,
                freezeAt = contest.freezeAt,
                registrationOpenDuring = contest.registrationOpenDuring,
                status = contest.status,
                visibility = contest.visibility,
                phase = phase,
                phaseLabel = phase.label,
                frozen = contest.frozenAt(now),
                unfrozenAt = contest.unfrozenAt,
                participantCount = participantCount,
                problems = problems,
            )
        }
    }
}

data class AdminContestProblemResponse(
    val problemId: Long,
    val label: String,
    val slug: String,
    val title: String,
    val seq: Int,
    val score: Int,
    val excluded: Boolean,
)
