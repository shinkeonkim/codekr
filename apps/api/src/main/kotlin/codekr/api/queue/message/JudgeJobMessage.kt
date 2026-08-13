package codekr.api.queue.message

import codekr.api.problem.entity.Problem
import codekr.api.problem.entity.OutputComparison
import codekr.api.problem.entity.ProblemKind
import codekr.api.problem.entity.ProblemRedisSpec
import codekr.api.problem.entity.ProblemSqlSpec
import codekr.api.problem.entity.ProblemTestcaseGroup
import codekr.api.submission.entity.Submission

data class JudgeJobMessage(
    val submissionId: Long,
    val problemId: Long,
    /**
     * 채점 방식 (#59). 채점기가 어느 구현으로 보낼지 이 값으로 고른다.
     *
     * **없으면 JUDGE_STDIO 다.** 이 필드가 없던 시절에 큐에 들어간 작업이 남아 있을 수
     * 있고, 그것들은 전부 stdin/stdout 채점이다. 채점기(Go)도 같게 읽는다.
     */
    val kind: ProblemKind = ProblemKind.JUDGE_STDIO,
    val runtimeId: String,
    val sourceCode: String,
    /**
     * 여러 파일로 낸 제출 (#457). 비면 `sourceCode` 하나다.
     *
     * **채점기는 DB 를 읽지 않는다** (ADR-0004) — 파일도 전부 실려 간다.
     */
    val sourceFiles: Map<String, String>? = null,
    val timeLimitMs: Int,
    val memoryLimitMb: Int,
    val testcases: List<JudgeTestcaseMessage>,
    /**
     * 부분 점수 묶음 (#473). 비면 지금까지처럼 전부 아니면 전무다.
     *
     * **채점기가 DB 를 읽지 않는다** (ADR-0004) — 묶음과 점수도 실려 간다.
     */
    val groups: List<JudgeTestcaseGroupMessage> = emptyList(),
    /**
     * 출력 비교 방식 (#279). **없으면 EXACT 다** — 이 필드가 없던 시절에 큐에 들어간
     * 작업이 남아 있을 수 있고, 그것들은 전부 정확 일치였다. 채점기(Go)도 같게 읽는다.
     */
    val comparison: OutputComparison = OutputComparison.EXACT,
    /** 허용 오차. `FLOAT` 일 때만 쓰인다. */
    val epsilon: Double = 0.0,
    /**
     * SQL 유형일 때만 실린다 (#60).
     *
     * 유형별 자료를 공통 필드에 섞지 않고 블록으로 나눈 이유: 유형이 늘어날 때
     * 쓰이지 않는 필드가 공통 계약에 쌓이면 어느 조합이 유효한지 알 수 없게 된다.
     */
    val sql: JudgeSqlSpecMessage? = null,
    /** Redis 유형일 때만 실린다 (#455). */
    val nosql: JudgeRedisSpecMessage? = null,
    /**
     * 함수형 유형일 때만 실린다 (#447, #421).
     *
     * **제출한 언어의 하네스다.** 실행기가 사용자 코드와 나란히 놓고 이것을 돌린다.
     * 채점기는 하네스가 비어 있으면 **채점하지 않는다** — 하네스 없이 함수만 있는
     * 코드를 돌리면 출력이 없고, 그것은 "틀렸다" 가 아니라 우리 잘못이다.
     */
    val harness: String? = null,
    /**
     * 스페셜 저지의 채점 코드 (#452). `comparison` 이 `CHECKER` 일 때만 실린다.
     *
     * **채점기가 DB 를 읽지 않는다**(ADR-0004) — 판정에 필요한 것은 전부 실려 간다.
     */
    val checker: String? = null,
    /**
     * 인터랙티브 문제의 채점 코드 (#474).
     *
     * **채점기가 DB 를 읽지 않는다** (ADR-0004) — 판정에 필요한 것은 전부 실려 간다.
     */
    val interactor: String? = null,
) {
    companion object {
        /**
         * 채점기가 DB 를 읽지 않도록 채점에 필요한 값을 모두 실어 보낸다 (ADR-0004).
         *
         * 제한은 **제출한 런타임에 맞는 값**을 고른다 (#97). 런타임별 오버라이드가 없으면
         * 문제 기본값이 그대로 나온다.
         */
        fun of(
            submission: Submission,
            problem: Problem,
            groups: List<ProblemTestcaseGroup> = emptyList(),
            sqlSpec: ProblemSqlSpec? = null,
            redisSpec: ProblemRedisSpec? = null,
        ): JudgeJobMessage {
            val limits = problem.limitsFor(submission.runtimeId)
            return JudgeJobMessage(
                submissionId = submission.id,
                problemId = problem.id,
                kind = problem.problemKind,
                runtimeId = submission.runtimeId,
                sourceCode = submission.sourceCode,
                sourceFiles = submission.sourceFiles,
                timeLimitMs = limits.timeLimitMs,
                memoryLimitMb = limits.memoryLimitMb,
                testcases = problem.testcases.map(JudgeTestcaseMessage::from),
                groups = groups.map(JudgeTestcaseGroupMessage::from),
                comparison = problem.outputComparison,
                epsilon = problem.floatEpsilon,
                sql = sqlSpec?.let(JudgeSqlSpecMessage::from),
                harness = when (problem.problemKind) {
                    ProblemKind.JUDGE_FUNCTION -> problem.harnessFor(submission.runtimeId)
                    else -> null
                },
                nosql = redisSpec?.let(JudgeRedisSpecMessage::from),
                interactor = problem.interactorSource
                    ?.takeIf { problem.problemKind == ProblemKind.JUDGE_INTERACTIVE },
                checker = problem.checkerSource
                    ?.takeIf { problem.outputComparison == OutputComparison.CHECKER },
            )
        }
    }
}
