package codekr.api.problem.dto

import codekr.api.problem.entity.Difficulty
import codekr.api.problem.entity.DifficultyTier
import codekr.api.problem.entity.Problem
import codekr.api.problem.entity.ProblemCategory

data class ProblemSummaryResponse(
    val id: Long,
    val slug: String,
    val title: String,
    val category: ProblemCategory,
    val difficulty: Difficulty,
    val difficultyLevel: Int,
    val tier: DifficultyTier,
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
            difficultyLevel = problem.difficultyLevel,
            tier = problem.difficulty.tier,
            difficultyLabel = problem.difficulty.label,
            timeLimitMs = problem.timeLimitMs,
            memoryLimitMb = problem.memoryLimitMb,
            published = problem.published,
            stats = stats,
        )
    }
}
