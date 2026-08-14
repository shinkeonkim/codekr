package codekr.api.queue.message

import codekr.api.submission.entity.JudgeOutcome
import codekr.api.submission.entity.Verdict

/** 채점 진행 이벤트. 알 수 없는 필드가 추가돼도 깨지지 않도록 모든 값에 기본값을 둔다. */
data class JudgeEventMessage(
    val type: String = "",
    val submissionId: Long = 0,
    val seq: Int = 0,
    val verdict: String? = null,
    val runtimeMs: Int = 0,
    val memoryKb: Int = 0,
    val passedCount: Int = 0,
    val totalCount: Int = 0,
    val maxRuntimeMs: Int = 0,
    val maxMemoryKb: Int = 0,
    val compileError: String? = null,
    val stderrExcerpt: String? = null,
    /**
     * 부분 점수 (#473). 묶음이 없는 문제에서는 0 이다.
     *
     * **이 값이 없던 시절의 작업이 남아 있을 수 있다** — 기본값이 0 인 이유다.
     */
    val score: Int = 0,
    val maxScore: Int = 0,
) {
    /** 알 수 없는 판정 값이 오면 인프라 문제로 본다 — 조용히 무시하지 않는다. */
    fun verdictOrSystemError(): Verdict =
        verdict?.let { runCatching { Verdict.valueOf(it) }.getOrNull() } ?: Verdict.SYSTEM_ERROR

    fun toOutcome() = JudgeOutcome(
        verdict = verdictOrSystemError(),
        passedCount = passedCount,
        totalCount = totalCount,
        maxRuntimeMs = maxRuntimeMs,
        maxMemoryKb = maxMemoryKb,
        compileError = compileError,
        score = score.takeIf { maxScore > 0 },
        maxScore = maxScore.takeIf { it > 0 },
    )

    companion object {
        const val TYPE_JUDGING = "JUDGING"
        const val TYPE_TESTCASE = "TESTCASE"
        const val TYPE_COMPLETED = "COMPLETED"
    }
}
