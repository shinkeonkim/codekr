package codekr.api.problem.dto

import codekr.api.problem.entity.Difficulty
import codekr.api.problem.entity.DifficultyTier
import codekr.api.problem.entity.Problem
import codekr.api.problem.entity.ProblemCategory
import codekr.api.runtime.RuntimeDefinition
import codekr.api.tag.dto.ProblemTagResponse

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
    /**
     * 알고리즘 분류 (#232).
     *
     * **답의 일부다.** "이 문제는 DP" 를 알고 푸는 것과 모르고 푸는 것은 다른 문제라,
     * 화면은 기본으로 접어 두고 펼쳤을 때만 보여 준다. 서버가 내리지 않는 방식(푼 뒤에만
     * 내려주기)은 택하지 않았다 — 그러면 "태그를 보려면 먼저 풀어야 한다" 가 되어,
     * 태그로 공부할 문제를 고르는 일 자체가 불가능해진다.
     */
    val tags: List<ProblemTagResponse>,
) {
    companion object {
        fun of(
            problem: Problem,
            runtimes: List<RuntimeDefinition>,
            stats: ProblemStats,
            tags: List<ProblemTagResponse>,
        ) = ProblemDetailResponse(
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
            tags = tags,
        )
    }
}
