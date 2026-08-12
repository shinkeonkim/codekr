package codekr.api.problem.dto

import codekr.api.problem.entity.Difficulty
import codekr.api.problem.entity.DifficultyState
import codekr.api.problem.entity.DifficultyTier
import codekr.api.problem.entity.Problem
import codekr.api.problem.entity.ProblemCategory

data class ProblemSummaryResponse(
    val id: Long,
    val slug: String,
    val title: String,
    val category: ProblemCategory,
    /** 미평가·평가안함이면 `null` 이다 (#195). */
    val difficulty: Difficulty?,
    val difficultyState: DifficultyState,
    val difficultyLevel: Int?,
    val tier: DifficultyTier?,
    val difficultyLabel: String,
    val timeLimitMs: Int,
    val memoryLimitMb: Int,
    val published: Boolean,
    /** 풀이 통계 (#84). 어드민 목록에서도 같은 값을 쓴다. */
    val stats: ProblemStats,
) {
    companion object {
        fun from(problem: Problem, stats: ProblemStats = ProblemStats.EMPTY) = ProblemSummaryResponse(
            id = problem.id,
            slug = problem.slug,
            title = problem.title,
            category = problem.category,
            difficulty = problem.difficulty,
            difficultyState = problem.difficultyState,
            difficultyLevel = problem.difficultyLevel,
            tier = problem.difficulty?.tier,
            // 표기는 늘 준다 — 화면이 상태별 문구를 다시 만들지 않게 (#195).
            difficultyLabel = problem.difficulty?.label ?: problem.difficultyState.label,
            timeLimitMs = problem.timeLimitMs,
            memoryLimitMb = problem.memoryLimitMb,
            published = problem.published,
            stats = stats,
        )
    }
}
