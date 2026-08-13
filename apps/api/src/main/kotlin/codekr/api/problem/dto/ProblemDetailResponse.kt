package codekr.api.problem.dto

import codekr.api.problem.entity.Difficulty
import codekr.api.problem.entity.DifficultyState
import codekr.api.problem.entity.DifficultyTier
import codekr.api.problem.entity.Problem
import codekr.api.problem.entity.ProblemKind
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
    /** 미평가·평가안함이면 `null` 이다 (#195). */
    val difficulty: Difficulty?,
    val difficultyState: DifficultyState,
    val difficultyLevel: Int?,
    val tier: DifficultyTier?,
    val difficultyLabel: String,
    /**
     * 누가 만들고 검수했는가 (#236).
     *
     * 탈퇴한 사람은 다른 곳과 같은 규칙으로 "탈퇴한 사용자" 가 된다 (#140).
     */
    val setters: List<ProblemCreditResponse>,
    val reviewers: List<ProblemCreditResponse>,
    /** 어디서 온 문제인가 (#236). 자체 제작이면 비어 있다. */
    val sourceLabel: String?,
    val sourceUrl: String?,
    val description: String,
    val inputDescription: String?,
    val outputDescription: String?,
    val timeLimitMs: Int,
    val memoryLimitMb: Int,
    val stats: ProblemStats,
    val examples: List<ProblemExampleResponse>,
    val runtimes: List<ProblemRuntimeResponse>,
    /**
     * 출제자가 언어를 좁혀 두었는가 (#419).
     *
     * **목록이 짧은 것을 화면이 고장으로 읽지 않게** 한다. 좁힌 것이면 화면이 그렇게
     * 말하고, 아니면 아무 말도 하지 않는다.
     */
    val runtimeRestricted: Boolean,
    /**
     * 문제 유형 (#450). **화면이 무엇을 쓰라고 말할지 정한다.**
     *
     * 함수형이면 "프로그램" 이 아니라 "함수" 를 쓰는 것이고, 그 사실을 모르면 사용자는
     * 입력을 읽는 코드부터 쓴다 — 그러면 하네스와 두 번 읽는다.
     */
    val problemKind: ProblemKind,
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
            credits: List<ProblemCreditResponse> = emptyList(),
        ) = ProblemDetailResponse(
            id = problem.id,
            slug = problem.slug,
            title = problem.title,
            category = problem.category,
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
            stats = stats,
            examples = problem.examples.map(ProblemExampleResponse::from),
            runtimeRestricted = problem.allowedRuntimeIds.isNotEmpty(),
            problemKind = problem.problemKind,
            runtimes = runtimes.map {
                ProblemRuntimeResponse.of(it, problem.templateOf(it.id), problem.limitsFor(it.id))
            },
            tags = tags,
        )
    }
}

/** 문제에 이름이 붙은 사람 하나 (#236). */
data class ProblemCreditResponse(
    val userId: Long,
    val nickname: String,
    val role: codekr.api.problem.credit.CreditRole,
    val roleLabel: String,
)
