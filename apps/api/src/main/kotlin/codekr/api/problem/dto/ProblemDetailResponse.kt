package codekr.api.problem.dto

import codekr.api.problem.entity.Difficulty
import codekr.api.problem.entity.DifficultyTier
import codekr.api.problem.entity.Problem
import codekr.api.problem.entity.ProblemCategory
import codekr.api.runtime.RuntimeDefinition

/**
 * 문제 상세. 히든 테스트케이스는 이 타입에 담기지 않으므로
 * 서비스 로직 실수로도 외부에 새어 나갈 수 없다.
 */
data class ProblemDetailResponse(
    val id: Long,
    val slug: String,
    val title: String,
    val category: ProblemCategory,
    val difficulty: Difficulty,
    val difficultyLevel: Int,
    val tier: DifficultyTier,
    val difficultyLabel: String,
    val description: String,
    val inputDescription: String?,
    val outputDescription: String?,
    val timeLimitMs: Int,
    val memoryLimitMb: Int,
    val stats: ProblemStats,
    val examples: List<ProblemExampleResponse>,
    val runtimes: List<ProblemRuntimeResponse>,
) {
    companion object {
        fun of(problem: Problem, runtimes: List<RuntimeDefinition>, stats: ProblemStats) = ProblemDetailResponse(
            id = problem.id,
            slug = problem.slug,
            title = problem.title,
            category = problem.category,
            difficulty = problem.difficulty,
            difficultyLevel = problem.difficultyLevel,
            tier = problem.difficulty.tier,
            difficultyLabel = problem.difficulty.label,
            description = problem.description,
            inputDescription = problem.inputDescription,
            outputDescription = problem.outputDescription,
            timeLimitMs = problem.timeLimitMs,
            memoryLimitMb = problem.memoryLimitMb,
            stats = stats,
            examples = problem.examples.map(ProblemExampleResponse::from),
            runtimes = runtimes.map {
                ProblemRuntimeResponse.of(it, problem.templateOf(it.id), problem.limitsFor(it.id))
            },
        )
    }
}
