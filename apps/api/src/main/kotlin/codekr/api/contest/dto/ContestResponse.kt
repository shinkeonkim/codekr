package codekr.api.contest.dto

import codekr.api.contest.entity.Contest
import codekr.api.contest.entity.ContestVisibility
import codekr.api.contest.entity.ContestPhase
import java.time.Instant

/** 대회 목록/상세의 공통 부분 (#61). */
data class ContestSummaryResponse(
    val slug: String,
    val title: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val phase: ContestPhase,
    val phaseLabel: String,
    val participantCount: Int,
    /** 순위가 동결돼 있는가 (#86). 화면이 크게 알려야 한다. */
    val frozen: Boolean,
    /**
     * 공개 범위 (#465).
     *
     * **화면이 "목록에 없는 대회" 라고 말해야 한다** — 링크를 받은 사람은 자기가 어디에
     * 들어왔는지 알아야 하고, 색인에서 빼는 판단(#278)도 이 값을 본다.
     */
    val visibility: ContestVisibility,
    val visibilityLabel: String,
) {
    companion object {
        fun of(contest: Contest, now: Instant, participantCount: Int): ContestSummaryResponse {
            val phase = contest.phaseAt(now)
            return ContestSummaryResponse(
                slug = contest.slug,
                visibility = contest.visibility,
                visibilityLabel = contest.visibility.label,
                title = contest.title,
                startsAt = contest.startsAt,
                endsAt = contest.endsAt,
                phase = phase,
                phaseLabel = phase.label,
                participantCount = participantCount,
                frozen = contest.frozenAt(now),
            )
        }
    }
}

data class ContestDetailResponse(
    val summary: ContestSummaryResponse,
    val description: String,
    val freezeAt: Instant?,
    val registered: Boolean,
    val canRegister: Boolean,
    /**
     * 문제 목록. **시작 전에는 비어 있다** — 참가자도 볼 수 없다 (#61 완료 조건).
     */
    val problems: List<ContestProblemResponse>,
)

data class ContestProblemResponse(
    /** 대회 안에서의 표기. A, B, C… */
    val label: String,
    /** 문제 번호 (#204). 주소가 번호로 간다. */
    val id: Long,
    val slug: String,
    val title: String,
    val score: Int,
    val excluded: Boolean,
)
