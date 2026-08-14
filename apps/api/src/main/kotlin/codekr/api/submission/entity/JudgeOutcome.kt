package codekr.api.submission.entity

/** 채점 완료 이벤트가 담고 있는 집계 결과. */
data class JudgeOutcome(
    val verdict: Verdict,
    val passedCount: Int,
    val totalCount: Int,
    val maxRuntimeMs: Int,
    val maxMemoryKb: Int,
    val compileError: String?,
    /**
     * 부분 점수 (#473). 묶음이 없는 문제에서는 null 이다.
     *
     * **null 과 0 을 가른다** — 0 점은 "다 틀렸다" 이고 null 은 "부분 점수가 없는
     * 문제다" 이다. 화면이 그 둘을 같게 그리면 만점짜리 문제가 0점으로 보인다.
     */
    val score: Int? = null,
    val maxScore: Int? = null,
)
