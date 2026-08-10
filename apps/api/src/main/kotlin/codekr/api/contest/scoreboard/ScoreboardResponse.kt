package codekr.api.contest.scoreboard

import java.time.Instant

/**
 * 대회 순위표 (#63).
 *
 * 일반 랭킹(#57, #85)과 **다른 화면이다.** 누적 실력이 아니라 그 대회 안에서의 순위이고,
 * 코드를 공유하지 않는다 — 공유하면 대회 규칙 변경이 일반 랭킹을 건드린다.
 */
data class ScoreboardResponse(
    val contestSlug: String,
    /** 참가자에게 순위가 감춰지는 중인가. 화면이 **크게** 알려야 한다. */
    val frozen: Boolean,
    /** 어느 시각까지의 결과를 보고 있는가. 동결 중이 아니면 null. */
    val frozenAt: Instant?,
    /**
     * 재채점이 도는 중인가.
     *
     * 중간 상태의 순위를 보여주면 참가자가 잘못된 정보로 판단한다.
     */
    val rejudgeInProgress: Boolean,
    val problems: List<ScoreboardProblem>,
    val rows: List<ScoreboardRow>,
)

data class ScoreboardProblem(
    /** 대회 안에서의 표기. A, B, C… */
    val label: String,
    val slug: String,
    val title: String,
    val score: Int,
    val excluded: Boolean,
    /** 몇 명이 풀었는지. 문제가 얼마나 어려웠는지를 그 자리에서 보여준다. */
    val solvedCount: Int,
)

data class ScoreboardRow(
    val rank: Int,
    val nickname: String,
    val totalScore: Int,
    val solvedCount: Int,
    /** 마지막 득점 시각. 동점 처리의 두 번째 키다. */
    val lastSolvedAt: Instant?,
    /** 문제 순서대로. `problems` 와 같은 길이다. */
    val cells: List<ScoreboardCellResponse>,
)

data class ScoreboardCellResponse(
    val solved: Boolean,
    /** 맞힌 시각까지 걸린 분. 못 맞혔으면 null. */
    val solvedMinutes: Int?,
    val attempts: Int,
    /** 동결 이후의 시도 수. 결과는 감춰지고 시도했다는 사실만 보인다 (#86). */
    val pending: Int,
)
