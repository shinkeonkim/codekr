package codekr.api.problem.admin.dto

import codekr.api.problem.entity.Difficulty
import codekr.api.problem.entity.DifficultyTier
import codekr.api.problem.entity.Problem
import codekr.api.problem.entity.ProblemJudgePriority
import codekr.api.problem.entity.ProblemCategory
import codekr.api.problem.entity.ProblemKind
import codekr.api.problem.entity.ProblemSqlSpec

/** 어드민 편집 화면용 상세 — 히든 테스트케이스와 언어별 초기 코드를 포함한다. */
data class AdminProblemDetailResponse(
    val id: Long,
    val slug: String,
    val title: String,
    val category: ProblemCategory,
    val problemKind: ProblemKind,
    /** SQL 유형이 아니면 null (#60). */
    val sqlSpec: SqlSpecResponse? = null,
    val difficulty: Difficulty,
    val difficultyLevel: Int,
    val tier: DifficultyTier,
    val difficultyLabel: String,
    val description: String,
    val inputDescription: String?,
    val outputDescription: String?,
    val timeLimitMs: Int,
    val memoryLimitMb: Int,
    val published: Boolean,
    val testcases: List<TestcaseResponse>,
    val templates: List<TemplateResponse>,
    val runtimeLimits: List<RuntimeLimitResponse>,
    val judgePriority: ProblemJudgePriority,
    val solution: SolutionResponse?,
    val verification: VerificationResponse?,
) {
    companion object {
        fun from(
            problem: Problem,
            verification: VerificationResponse? = null,
            sqlSpec: ProblemSqlSpec? = null,
        ) = AdminProblemDetailResponse(
            id = problem.id,
            slug = problem.slug,
            title = problem.title,
            category = problem.category,
            problemKind = problem.problemKind,
            sqlSpec = sqlSpec?.let(SqlSpecResponse::from),
            difficulty = problem.difficulty,
            difficultyLevel = problem.difficultyLevel,
            tier = problem.difficulty.tier,
            difficultyLabel = problem.difficulty.label,
            description = problem.description,
            inputDescription = problem.inputDescription,
            outputDescription = problem.outputDescription,
            timeLimitMs = problem.timeLimitMs,
            memoryLimitMb = problem.memoryLimitMb,
            published = problem.published,
            testcases = problem.testcases.map(TestcaseResponse::from),
            templates = problem.templates.map(TemplateResponse::from),
            runtimeLimits = problem.runtimeLimits.map(RuntimeLimitResponse::from),
            judgePriority = problem.judgePriority,
            solution = SolutionResponse.from(problem),
            verification = verification,
        )
    }
}
