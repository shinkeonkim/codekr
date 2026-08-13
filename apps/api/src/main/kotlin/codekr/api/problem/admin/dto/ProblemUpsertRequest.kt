package codekr.api.problem.admin.dto

import codekr.api.problem.entity.Difficulty
import codekr.api.problem.entity.DifficultyState
import codekr.api.problem.entity.ExecutionLimits
import codekr.api.problem.entity.ProblemCategory
import codekr.api.problem.entity.ProblemJudgePriority
import codekr.api.problem.entity.OutputComparison
import codekr.api.problem.entity.ProblemKind
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/** 문제 등록/수정에 공통으로 쓰는 요청. 테스트케이스와 템플릿은 항상 전체 치환된다. */
data class ProblemUpsertRequest(
    @field:Pattern(
        regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
        message = "slug 는 소문자, 숫자, 하이픈만 사용할 수 있습니다.",
    )
    @field:Size(min = 2, max = 120)
    val slug: String,

    @field:NotBlank @field:Size(max = 200)
    val title: String,

    val category: ProblemCategory,

    /** 채점 방식 (#59). 아직 구현이 없는 유형은 서비스가 막는다. */
    val problemKind: ProblemKind = ProblemKind.JUDGE_STDIO,

    /**
     * 출제자·검수자 (#236). 회원 id 다 — 화면이 **닉네임으로 찾아 고른다.**
     *
     * 여기까지 오는 것은 id 지만, 사람이 id 를 손으로 치게 하지 않는다 (#223 과 같은 문제).
     */
    val setterIds: List<Long> = emptyList(),
    val reviewerIds: List<Long> = emptyList(),

    /**
     * 출처 (#236). **선택이다** — 필수로 두면 자체 제작 문제에 "자체 제작" 을 매번 적게 된다.
     */
    @field:Size(max = 200)
    val sourceLabel: String? = null,

    @field:Size(max = 500)
    val sourceUrl: String? = null,

    /**
     * 이 문제를 풀 수 있는 런타임 (#419).
     *
     * **비우면 그 종류의 전부를 허용한다.** 언어를 새로 들일 때 기존 문제를 전부
     * 손보게 하지 않으려는 것이고, 지금 문제들이 그대로 돌아야 하기 때문이다.
     * 하나라도 고르면 그 목록만 허용된다.
     */
    val allowedRuntimeIds: List<String> = emptyList(),

    /**
     * 함수형 문제의 언어별 하네스 (#446). `런타임 id → 하네스 소스`.
     *
     * **하네스를 쓴 언어가 곧 풀 수 있는 언어다** — `allowedRuntimeIds` 를 따로
     * 고르게 하지 않는다. 함수형이 아닌 문제에 실려 오면 서비스가 거부한다.
     */
    val harnesses: Map<String, String> = emptyMap(),

    /** SQL 유형일 때만 쓴다 (#60). 다른 유형에 실려 오면 서비스가 거부한다. */
    @field:Valid
    val sqlSpec: SqlSpecRequest? = null,

    /**
     * 난이도 (#195). **비워 둘 수 있다** — 실제 난이도는 사람들이 풀어 봐야 아는 값이라,
     * 등록 시점에 아무 값이나 박아 넣으면 그 숫자가 곧바로 점수가 되어 랭킹에 반영된다.
     *
     * 비우면 `difficultyState` 가 뜻을 갖는다.
     */
    val difficulty: Difficulty? = null,

    /**
     * 난이도 상태 (#195). 기본은 **미평가**다.
     *
     * 기본을 브론즈로 두면 "아직 안 정했다" 와 "쉽다" 가 구분되지 않는다 — 등록은
     * 쉬워지지만 거짓 정보가 섞인다.
     */
    val difficultyState: DifficultyState = DifficultyState.UNRATED,

    @field:NotBlank
    val description: String,

    val inputDescription: String? = null,
    val outputDescription: String? = null,

    @field:Min(ExecutionLimits.MIN_TIME_LIMIT_MS.toLong())
    @field:Max(ExecutionLimits.MAX_TIME_LIMIT_MS.toLong())
    val timeLimitMs: Int = ExecutionLimits.DEFAULT_TIME_LIMIT_MS,

    @field:Min(ExecutionLimits.MIN_MEMORY_LIMIT_MB.toLong())
    @field:Max(ExecutionLimits.MAX_MEMORY_LIMIT_MB.toLong())
    val memoryLimitMb: Int = ExecutionLimits.DEFAULT_MEMORY_LIMIT_MB,

    /**
     * 출력 비교 방식 (#279). **기본은 정확 일치** — 적지 않으면 지금까지의 동작이다.
     */
    val outputComparison: OutputComparison = OutputComparison.EXACT,

    /**
     * 허용 오차. `FLOAT` 일 때만 쓰인다.
     *
     * 위쪽을 막아 두는 이유: 오차를 크게 잡으면 **틀린 답이 통과한다.** 1 이면
     * `3.5` 문제에서 `4.4` 가 맞은 답이 된다 — 그것은 실수를 허용하는 것이 아니라
     * 채점을 끄는 것이다.
     */
    @field:DecimalMin("0.0")
    @field:DecimalMax("0.1")
    val floatEpsilon: Double = 0.0,

    val published: Boolean = false,

    @field:Valid
    val testcases: List<TestcaseRequest> = emptyList(),

    @field:Valid
    /**
     * 채점 우선순위 (#102). 실행이 무거워 큐를 오래 잡는 문제를 뒤로 미룰 때 쓴다.
     * 최상위(HIGH)는 고를 수 없다 — 시스템 동작에만 남긴다.
     */
    val judgePriority: ProblemJudgePriority = ProblemJudgePriority.NORMAL,

    val templates: List<TemplateRequest> = emptyList(),

    /** 런타임별 실행 제한 오버라이드 (#97). 적지 않은 런타임은 위 기본 제한을 쓴다. */
    @field:Valid
    val runtimeLimits: List<RuntimeLimitRequest> = emptyList(),

    /** 선택 사항. 넣으면 전체 테스트케이스를 이 코드로 검증할 수 있다 (#39). */
    @field:Valid
    val solution: SolutionRequest? = null,
)
