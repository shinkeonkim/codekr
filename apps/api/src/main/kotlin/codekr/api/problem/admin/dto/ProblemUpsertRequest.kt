package codekr.api.problem.admin.dto

import codekr.api.problem.entity.Difficulty
import codekr.api.problem.entity.DifficultyState
import codekr.api.problem.entity.ExecutionLimits
import codekr.api.problem.entity.ProblemCategory
import codekr.api.problem.entity.ProblemJudgePriority
import codekr.api.problem.entity.OutputComparison
import codekr.api.problem.entity.ProblemFile
import codekr.api.problem.entity.ProblemKind
import codekr.api.problem.entity.ProblemTestcaseGroup
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
     * 스페셜 저지의 채점 코드 (#452). 파이썬이다.
     *
     * `outputComparison` 이 `CHECKER` 일 때만 쓴다 — 아니면 서비스가 거부한다.
     * 입력·제출 출력·정답 출력을 **파일로** 받고, 종료 코드로 답한다 (0=맞음, 1=틀림).
     */
    val checkerSource: String? = null,

    /** SQL 유형일 때만 쓴다 (#60). 다른 유형에 실려 오면 서비스가 거부한다. */
    @field:Valid
    val sqlSpec: SqlSpecRequest? = null,

    /** NoSQL 유형일 때만 싣는다 (#455). */
    @field:Valid
    val nosqlSpec: NoSqlSpecRequest? = null,

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

    /**
     * 여러 파일을 완성하는 문제의 파일 목록 (#457). 비면 파일 하나짜리 문제다.
     *
     * 순서가 뜻을 갖는다 — **첫 파일이 진입점**이고, 화면의 탭 차례이기도 하다.
     */
    @field:Valid
    val files: List<ProblemFileRequest> = emptyList(),

    /**
     * 부분 점수 묶음 (#473). 비면 지금까지처럼 전부 아니면 전무다.
     *
     * **묶음 안을 다 맞혀야 그 점수를 받는다** (IOI 관례).
     */
    @field:Valid
    val testcaseGroups: List<TestcaseGroupRequest> = emptyList(),

    /** 런타임별 실행 제한 오버라이드 (#97). 적지 않은 런타임은 위 기본 제한을 쓴다. */
    @field:Valid
    val runtimeLimits: List<RuntimeLimitRequest> = emptyList(),

    /** 선택 사항. 넣으면 전체 테스트케이스를 이 코드로 검증할 수 있다 (#39). */
    @field:Valid
    val solution: SolutionRequest? = null,
)

/**
 * 여러 파일을 완성하는 문제의 파일 하나 (#457).
 *
 * **런타임마다 목록이 다르다.** 같은 문제라도 자바는 `Main.java`·`Helper.java` 이고
 * 파이썬은 `main.py`·`helper.py` 다.
 */
data class ProblemFileRequest(
    @field:NotBlank(message = "실행 환경은 필수입니다.")
    val runtimeId: String,

    @field:NotBlank(message = "파일 이름은 필수입니다.")
    @field:Size(max = 60)
    val name: String,

    val template: String = "",

    /** 거짓이면 제출에 실리지 않고 서버가 시작 코드를 그대로 쓴다. */
    val editable: Boolean = true,
) {
    fun toEntity(problemId: Long, seq: Int) = ProblemFile(
        problemId = problemId,
        runtimeId = runtimeId,
        seq = seq,
        name = name,
        template = template,
        editable = editable,
    )
}

/** 부분 점수 묶음 (#473). */
data class TestcaseGroupRequest(
    @field:Min(1) val groupNo: Int,
    @field:Min(0) val score: Int,
    /** "N ≤ 1,000" 처럼 제약을 그대로 적으면 그것이 힌트가 된다. */
    @field:Size(max = 60) val label: String = "",
) {
    fun toEntity(problemId: Long) = ProblemTestcaseGroup(problemId, groupNo, score, label)
}
