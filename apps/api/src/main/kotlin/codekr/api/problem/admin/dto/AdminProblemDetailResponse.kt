package codekr.api.problem.admin.dto

import codekr.api.problem.entity.Difficulty
import codekr.api.problem.entity.DifficultyState
import codekr.api.problem.entity.DifficultyTier
import codekr.api.problem.entity.OutputComparison
import codekr.api.problem.entity.Problem
import codekr.api.problem.entity.ProblemJudgePriority
import codekr.api.problem.entity.ProblemCategory
import codekr.api.problem.entity.ProblemKind
import codekr.api.problem.entity.ProblemSqlSpec
import codekr.api.tag.dto.ProblemTagResponse

/** 어드민 편집 화면용 상세 — 히든 테스트케이스와 언어별 초기 코드를 포함한다. */
data class AdminProblemDetailResponse(
    val id: Long,
    val slug: String,
    val title: String,
    val category: ProblemCategory,
    val problemKind: ProblemKind,
    /** SQL 유형이 아니면 null (#60). */
    val sqlSpec: SqlSpecResponse? = null,
    /** 편집 화면이 지금 붙어 있는 사람을 그대로 보여야 한다 (#236). */
    val setters: List<codekr.api.problem.dto.ProblemCreditResponse> = emptyList(),
    val reviewers: List<codekr.api.problem.dto.ProblemCreditResponse> = emptyList(),
    val sourceLabel: String? = null,
    val sourceUrl: String? = null,
    /** 미평가·평가안함이면 `null` 이다 (#195). */
    val difficulty: Difficulty?,
    val difficultyState: DifficultyState,
    val difficultyLevel: Int?,
    val tier: DifficultyTier?,
    val difficultyLabel: String,
    val description: String,
    val inputDescription: String?,
    val outputDescription: String?,
    val timeLimitMs: Int,
    val memoryLimitMb: Int,
    /** 출력 비교 방식과 허용 오차 (#279). 편집 화면이 그대로 되돌려 보낸다. */
    val outputComparison: OutputComparison,
    val floatEpsilon: Double,
    val published: Boolean,
    val testcases: List<TestcaseResponse>,
    val templates: List<TemplateResponse>,
    val runtimeLimits: List<RuntimeLimitResponse>,
    /** 풀 수 있는 런타임 (#419). **비어 있으면 그 종류 전부**를 허용한다는 뜻이다. */
    val allowedRuntimeIds: List<String>,
    val judgePriority: ProblemJudgePriority,
    val solution: SolutionResponse?,
    /**
     * 스페셜 저지의 채점 코드 (#452). **어드민에게만 간다** — 공개 상세에는 없다.
     *
     * 고치려면 지금 무엇이 들어 있는지 보여야 한다.
     */
    val checkerSource: String?,
    val verification: VerificationResponse?,
    /** 지금 붙어 있는 태그 (#232). 편집 화면이 무엇을 고쳐야 할지 알려면 필요하다. */
    val tags: List<ProblemTagResponse> = emptyList(),
) {
    companion object {
        fun from(
            problem: Problem,
            verification: VerificationResponse? = null,
            sqlSpec: ProblemSqlSpec? = null,
            tags: List<ProblemTagResponse> = emptyList(),
            credits: List<codekr.api.problem.dto.ProblemCreditResponse> = emptyList(),
        ) = AdminProblemDetailResponse(
            id = problem.id,
            slug = problem.slug,
            title = problem.title,
            category = problem.category,
            problemKind = problem.problemKind,
            sqlSpec = sqlSpec?.let(SqlSpecResponse::from),
            setters = credits.filter { it.role == codekr.api.problem.credit.CreditRole.SETTER },
            reviewers = credits.filter { it.role == codekr.api.problem.credit.CreditRole.REVIEWER },
            sourceLabel = problem.sourceLabel,
            sourceUrl = problem.sourceUrl,
            difficulty = problem.difficulty,
            difficultyState = problem.difficultyState,
            difficultyLevel = problem.difficultyLevel,
            tier = problem.difficulty?.tier,
            // 표기는 늘 준다 — 화면이 상태별 문구를 다시 만들지 않게 (#195).
            difficultyLabel = problem.difficulty?.label ?: problem.difficultyState.label,
            description = problem.description,
            inputDescription = problem.inputDescription,
            outputDescription = problem.outputDescription,
            timeLimitMs = problem.timeLimitMs,
            memoryLimitMb = problem.memoryLimitMb,
            outputComparison = problem.outputComparison,
            floatEpsilon = problem.floatEpsilon,
            published = problem.published,
            testcases = problem.testcases.map(TestcaseResponse::from),
            templates = problem.templates.map(TemplateResponse::from),
            runtimeLimits = problem.runtimeLimits.map(RuntimeLimitResponse::from),
            allowedRuntimeIds = problem.allowedRuntimeIds,
            judgePriority = problem.judgePriority,
            solution = SolutionResponse.from(problem),
            checkerSource = problem.checkerSource,
            verification = verification,
            tags = tags,
        )
    }
}
