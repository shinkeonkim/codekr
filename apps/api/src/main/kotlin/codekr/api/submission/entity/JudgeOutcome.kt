package codekr.api.submission.entity

/** 채점 완료 이벤트가 담고 있는 집계 결과. */
data class JudgeOutcome(
    val verdict: Verdict,
    val passedCount: Int,
    val totalCount: Int,
    val maxRuntimeMs: Int,
    val maxMemoryKb: Int,
    val compileError: String?,
)
