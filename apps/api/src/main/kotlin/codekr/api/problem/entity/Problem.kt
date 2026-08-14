package codekr.api.problem.entity

import codekr.api.common.entity.SoftDeletableEntity
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import java.security.MessageDigest
import java.time.Instant

@Entity
@Table(name = "problems")
class Problem(

    // 유일성은 살아 있는 행에만 적용되는 부분 유니크 인덱스가 담당한다 (V1__init.sql).
    @Column(nullable = false)
    var slug: String,

    @Column(nullable = false)
    var title: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var category: ProblemCategory,

    /**
     * 채점 방식 (#59). 카테고리(무엇에 대한 문제인가)와는 다른 축이다.
     *
     * 지금 있는 문제는 전부 stdin/stdout 이라 기본값이 그것이다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "problem_kind", nullable = false, length = 20)
    var problemKind: ProblemKind = ProblemKind.JUDGE_STDIO,

    /**
     * 난이도를 정수로 저장하는 이유: 티어 범위 검색과 정렬을 인덱스로 처리하기 위함이다.
     * 애플리케이션에서는 [difficulty] 로만 다루고 이 값을 직접 쓰지 않는다.
     */
    @Column(name = "difficulty_level")
    var difficultyLevel: Int? = null,

    /**
     * 난이도가 매겨졌는가 (#195).
     *
     * 레벨과 어긋나지 않게 DB 가 못 박는다 — `RATED` 이면 레벨이 있고, 아니면 없다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty_state", nullable = false, length = 16)
    var difficultyState: DifficultyState = DifficultyState.RATED,

    @Column(nullable = false, columnDefinition = "text")
    var description: String,

    @Column(name = "input_description", columnDefinition = "text")
    var inputDescription: String? = null,

    @Column(name = "output_description", columnDefinition = "text")
    var outputDescription: String? = null,

    @Column(name = "time_limit_ms", nullable = false)
    var timeLimitMs: Int = ExecutionLimits.DEFAULT_TIME_LIMIT_MS,

    @Column(name = "memory_limit_mb", nullable = false)
    var memoryLimitMb: Int = ExecutionLimits.DEFAULT_MEMORY_LIMIT_MB,

    /**
     * 출력 비교 방식 (#279, ADR-0010).
     *
     * **기본은 정확 일치다.** 이미 등록된 문제의 판정이 하나도 바뀌면 안 된다 —
     * 비교 방식이 바뀌면 재채점(#107)에서 판정이 뒤집힌다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "output_comparison", nullable = false, length = 16)
    var outputComparison: OutputComparison = OutputComparison.EXACT,

    /**
     * 허용 오차. `FLOAT` 일 때만 쓰인다.
     *
     * 절대·상대 중 **하나만 만족해도** 맞다고 본다 — 답이 0 근처일 때와 아주 클 때
     * 같은 잣대를 쓸 수 없다.
     */
    @Column(name = "float_epsilon", nullable = false)
    var floatEpsilon: Double = 0.0,

    /** 채점 큐 우선순위 (#102). 실행이 무거운 문제를 뒤로 미룰 수 있다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "judge_priority", nullable = false, length = 20)
    var judgePriority: ProblemJudgePriority = ProblemJudgePriority.NORMAL,

    @Column(nullable = false)
    var published: Boolean = false,

    @Column(name = "created_by")
    var createdBy: Long? = null,

    /**
     * 출처 (#236). 사람이 아니라 **바깥의 무언가**를 가리킨다.
     *
     * **라벨과 링크는 한 쌍이다** — 링크만 있으면 무엇인지 모르고, 라벨만 있으면
     * 확인할 수 없다. 다만 링크 없는 출처(책·대회 이름)는 있으므로 링크만 비울 수 있다.
     *
     * **필수가 아니다.** 필수로 두면 자체 제작 문제에 "자체 제작" 을 매번 적게 된다.
     */
    @Column(name = "source_label", length = 200)
    var sourceLabel: String? = null,

    @Column(name = "source_url", length = 500)
    var sourceUrl: String? = null,

) : SoftDeletableEntity() {

    /**
     * 어드민이 등록한 정답 코드. 선택 사항이며 공개 응답 DTO 에는 필드 자체를 만들지 않는다.
     * 히든 테스트케이스와 같은 방식으로 구조에서 막는다.
     */
    @Column(name = "solution_runtime_id", length = 40)
    var solutionRuntimeId: String? = null

    @Column(name = "solution_source_code", columnDefinition = "text")
    var solutionSourceCode: String? = null

    /** 마지막 검증 실행(제출)의 ID. */
    @Column(name = "verification_submission_id")
    var verificationSubmissionId: Long? = null

    /** 검증을 시작한 시점의 채점 관련 내용 지문. 지금 값과 다르면 그 결과는 낡은 것이다. */
    @Column(name = "verified_signature", length = 64)
    var verifiedSignature: String? = null

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    /**
     * 저장 컬럼이 아니라 [difficultyLevel] 을 읽고 쓰는 창구다 (뒷받침 필드가 없어 JPA 가 매핑하지 않는다).
     *
     * **난이도가 없을 수 있다** (#195). 미평가·평가안함이면 `null` 이고, 그때 점수는 0 이다.
     */
    var difficulty: Difficulty?
        get() = Difficulty.ofLevelOrNull(difficultyLevel)
        set(value) {
            difficultyLevel = value?.level
            difficultyState = if (value == null) difficultyState.takeIf { !it.scored } ?: DifficultyState.UNRATED
            else DifficultyState.RATED
        }

    // 소프트 삭제된 자식까지 담고 있는 원본 컬렉션. 밖으로는 살아 있는 것만 노출한다.
    @OneToMany(mappedBy = "problem", cascade = [CascadeType.ALL])
    @OrderBy("seq ASC")
    private val allTestcases: MutableList<ProblemTestcase> = mutableListOf()

    @OneToMany(mappedBy = "problem", cascade = [CascadeType.ALL])
    private val allTemplates: MutableList<ProblemTemplate> = mutableListOf()

    @OneToMany(mappedBy = "problem", cascade = [CascadeType.ALL], orphanRemoval = true)
    private val allowedRuntimes: MutableList<ProblemAllowedRuntime> = mutableListOf()

    @OneToMany(mappedBy = "problem", cascade = [CascadeType.ALL], orphanRemoval = true)
    private val harnesses: MutableList<ProblemHarness> = mutableListOf()

    @OneToMany(mappedBy = "problem", cascade = [CascadeType.ALL])
    private val allRuntimeLimits: MutableList<ProblemRuntimeLimit> = mutableListOf()

    val testcases: List<ProblemTestcase> get() = allTestcases.filter { !it.isDeleted }

    val examples: List<ProblemTestcase>
        get() = testcases.filter { it.visibility == TestcaseVisibility.PUBLIC }

    val templates: List<ProblemTemplate> get() = allTemplates.filter { !it.isDeleted }

    val runtimeLimits: List<ProblemRuntimeLimit> get() = allRuntimeLimits.filter { !it.isDeleted }

    /**
     * 풀 수 있는 런타임 (#419). **비어 있으면 전부 허용**이다.
     *
     * 빈 목록과 "전부 허용" 을 구분하지 않는 것이 아니다 — 어드민 화면에서 하나도
     * 고르지 않는 것이 곧 "전부 허용" 이고, 그 문구를 화면이 적는다. 하나라도 고르면
     * 그 목록만 허용된다.
     */
    val allowedRuntimeIds: List<String>
        get() = when (problemKind) {
            // 함수형은 하네스가 곧 허용 목록이다 (#446).
            ProblemKind.JUDGE_FUNCTION -> harnessRuntimeIds
            else -> allowedRuntimes.map { it.runtimeId }.sorted()
        }

    /**
     * 함수형 문제의 하네스 (#446). **하네스를 쓴 언어가 곧 풀 수 있는 언어다.**
     *
     * 허용 목록(#419)을 손으로 또 고르게 하지 않는다 — 두 곳이 같은 것을 정하면 어긋난다.
     */
    val harnessRuntimeIds: List<String> get() = harnesses.map { it.runtimeId }.sorted()

    fun harnessFor(runtimeId: String): String? =
        harnesses.firstOrNull { it.runtimeId == runtimeId }?.source

    /**
     * 하네스를 갈아 끼운다 (#446).
     *
     * **남는 런타임의 행은 그대로 두고 내용만 고친다.** 지웠다 다시 넣으면
     * [replaceAllowedRuntimes] 와 같은 이유로 유니크 제약에 걸린다 (#560).
     */
    fun replaceHarnesses(sources: Map<String, String>) {
        val wanted = sources.filterValues { it.isNotBlank() }.toSortedMap()
        harnesses.removeIf { it.runtimeId !in wanted.keys }
        harnesses.forEach { it.source = wanted.getValue(it.runtimeId) }
        wanted.filterKeys { runtimeId -> harnesses.none { it.runtimeId == runtimeId } }
            .forEach { (runtimeId, source) ->
                harnesses.add(ProblemHarness(runtimeId, source).also { it.assignTo(this) })
            }
    }

    /** 이 런타임으로 풀 수 있는가 (#419, #446). 허용 목록이 비어 있으면 무엇이든 좋다. */
    fun allowsRuntime(runtimeId: String): Boolean = when {
        /*
            **함수형 문제는 하네스가 있는 언어로만 풀린다** (#446).

            허용 목록을 따로 두지 않는다 — 하네스를 쓴 것이 곧 "이 언어로 낼 수 있다"
            는 뜻이고, 두 곳이 같은 것을 정하면 어긋난다.
        */
        problemKind == ProblemKind.JUDGE_FUNCTION -> harnesses.any { it.runtimeId == runtimeId }
        allowedRuntimes.isEmpty() -> true
        else -> allowedRuntimes.any { it.runtimeId == runtimeId }
    }

    /**
     * 허용 목록을 갈아 끼운다 (#419).
     *
     * **남는 것은 건드리지 않는다.** `clear()` 로 비우고 같은 `runtime_id` 를 다시 넣으면
     * `uq_problem_allowed_runtimes` 에 걸린다 (#560) — Hibernate 가 한 flush 안에서
     * **INSERT 를 DELETE 보다 먼저** 내보내기 때문이다. 지운 행의 DELETE 가 나가기 전에
     * 같은 키의 INSERT 가 먼저 도착한다.
     *
     * 테스트케이스는 사이에 `flush()` 를 껴서 같은 함정을 피했는데(부분 유니크 인덱스),
     * 여기서는 **애초에 지우지 않는 편**이 낫다. 허용 목록은 기록이 아니라 지금의
     * 규칙이라, 값이 그대로면 행도 그대로인 것이 맞다.
     */
    fun replaceAllowedRuntimes(runtimeIds: List<String>) {
        val wanted = runtimeIds.distinct()
        allowedRuntimes.removeIf { it.runtimeId !in wanted }
        wanted.filter { runtimeId -> allowedRuntimes.none { it.runtimeId == runtimeId } }
            .forEach { allowedRuntimes.add(ProblemAllowedRuntime(it).also { r -> r.assignTo(this) }) }
    }

    /** 런타임별 초기 코드. 문제에 지정된 값이 없으면 호출자가 런타임 기본 템플릿을 쓴다. */
    fun templateOf(runtimeId: String): String? =
        templates.firstOrNull { it.runtimeId == runtimeId }?.sourceCode

    /**
     * 테스트케이스와 템플릿은 부분 수정하지 않고 항상 통째로 교체한다.
     *
     * 교체는 "기존 것을 소프트 삭제 → 새로 추가" 두 단계다. `(problem_id, seq)` 부분 유니크 인덱스가
     * 살아 있는 행에만 걸려 있으므로, 삭제가 DB 에 먼저 반영되어야 같은 순번을 다시 넣을 수 있다.
     * 그래서 두 단계를 분리해 호출자가 사이에 flush 할 수 있게 한다.
     */
    fun softDeleteTestcases() = allTestcases.forEach { it.softDelete() }

    fun addTestcases(testcases: List<ProblemTestcase>) {
        testcases.forEach { it.assignTo(this) }
        allTestcases.addAll(testcases)
    }

    fun softDeleteTemplates() = allTemplates.forEach { it.softDelete() }

    fun addTemplates(templates: List<ProblemTemplate>) {
        templates.forEach { it.assignTo(this) }
        allTemplates.addAll(templates)
    }

    fun softDeleteRuntimeLimits() = allRuntimeLimits.forEach { it.softDelete() }

    fun addRuntimeLimits(limits: List<ProblemRuntimeLimit>) {
        limits.forEach { it.assignTo(this) }
        allRuntimeLimits.addAll(limits)
    }

    /**
     * 이 런타임으로 실행할 때 적용할 제한.
     *
     * 런타임별 오버라이드가 없으면 문제 기본값을 쓴다. **제출·실행·정답 검증이 모두
     * 이 한 곳을 거쳐야** 한다 — 세 경로가 각자 제한을 고르면 서로 달라진다.
     */
    fun limitsFor(runtimeId: String): ResolvedLimits =
        runtimeLimits.firstOrNull { it.runtimeId == runtimeId }
            ?.let { ResolvedLimits(it.timeLimitMs, it.memoryLimitMb, overridden = true) }
            ?: ResolvedLimits(timeLimitMs, memoryLimitMb, overridden = false)

    /**
     * 채점 단위의 개수 (#60). 진행률의 분모다.
     *
     * stdin/stdout 은 테스트케이스 수, SQL 은 정답 쿼리 하나와의 비교라 1 이다.
     */
    val judgeUnitCount: Int
        get() = when (problemKind) {
            ProblemKind.JUDGE_SQL -> 1
            else -> testcases.size
        }

    val hasSolution: Boolean get() = !solutionSourceCode.isNullOrBlank() && solutionRuntimeId != null

    /**
     * 채점 결과를 좌우하는 내용의 지문.
     *
     * 지문에 넣는 것은 **판정을 바꿀 수 있는 값만**이다 — 테스트케이스, 실행 제한, 정답 코드.
     * 지문을 두는 이유는 수정 시각을 쓸 수 없기 때문이다. 검증 기록을 저장하는 순간
     * `updatedAt` 이 바뀌어 결과가 항상 낡은 것으로 표시된다.
     */
    fun verificationSignature(): String {
        val content = buildString {
            append(timeLimitMs).append('|').append(memoryLimitMb).append('|')
            // 비교 방식도 판정을 바꾼다 (#279). 빠뜨리면 오차만 고쳤을 때 검증이
            // 낡지 않은 것으로 남는다.
            append(outputComparison).append('/').append(floatEpsilon).append('|')
            // 런타임별 제한도 판정을 바꾼다. 빠뜨리면 제한만 고쳤을 때 검증이 낡지 않은 것으로 남는다.
            runtimeLimits.sortedBy { it.runtimeId }.forEach {
                append(it.runtimeId).append('=').append(it.timeLimitMs).append('/')
                    .append(it.memoryLimitMb).append(';')
            }
            append('|')
            append(solutionRuntimeId).append('|').append(solutionSourceCode).append('|')
            testcases.sortedBy { it.seq }.forEach {
                append(it.seq).append(':').append(it.input).append("=>").append(it.expectedOutput).append(';')
            }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(content.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    /** 검증 이후 채점에 영향을 주는 내용이 바뀌었는지. */
    val isVerificationStale: Boolean
        get() = verificationSubmissionId != null && verifiedSignature != verificationSignature()

    /**
     * 스페셜 저지의 채점 코드 (#452).
     *
     * **사용자에게 절대 내려가지 않는다.** 정답의 일부나 판정 방식이 들어간다 —
     * 문제 상세에도, 오류 메시지에도 나가지 않는다.
     */
    /**
     * 인터랙티브 문제에서 **대화를 주관하는 출제자의 코드** (#474).
     *
     * 채점 코드(#452)와 같은 규칙으로 다룬다 — **사용자에게 절대 내려가지 않는다.**
     */
    @Column(name = "interactor_source", columnDefinition = "text")
    var interactorSource: String? = null

    @Column(name = "checker_source", columnDefinition = "text")
    var checkerSource: String? = null

    fun replaceSolution(runtimeId: String?, sourceCode: String?) {
        val nextRuntimeId = runtimeId?.takeIf { !sourceCode.isNullOrBlank() }
        val nextSourceCode = sourceCode?.takeIf { it.isNotBlank() }
        if (nextRuntimeId == solutionRuntimeId && nextSourceCode == solutionSourceCode) return

        solutionRuntimeId = nextRuntimeId
        solutionSourceCode = nextSourceCode
        // 정답 코드 자체가 바뀌면 이전 검증은 다른 코드에 대한 결과라 남길 이유가 없다.
        // (테스트케이스만 바뀐 경우는 지우지 않고 '낡음'으로 표시한다 — 무엇이 달라졌는지 보이게)
        verificationSubmissionId = null
        verifiedSignature = null
    }

    fun startVerification(submissionId: Long) {
        verificationSubmissionId = submissionId
        verifiedSignature = verificationSignature()
    }

    /** 문제를 지우면 딸린 테스트케이스와 템플릿도 함께 지워진 것으로 본다. */
    fun delete(now: Instant = Instant.now()) {
        softDelete(now)
        allTestcases.forEach { it.softDelete(now) }
        allTemplates.forEach { it.softDelete(now) }
    }
}
