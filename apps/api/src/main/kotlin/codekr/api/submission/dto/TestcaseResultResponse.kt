package codekr.api.submission.dto

import codekr.api.submission.entity.SubmissionTestcaseResult
import codekr.api.submission.entity.Verdict

data class TestcaseResultResponse(
    val seq: Int,
    val verdict: Verdict,
    val runtimeMs: Int,
    val memoryKb: Int,
    val stderrExcerpt: String?,
) {
    companion object {
        fun from(result: SubmissionTestcaseResult) = TestcaseResultResponse(
            seq = result.seq,
            verdict = result.verdict,
            runtimeMs = result.runtimeMs,
            memoryKb = result.memoryKb,
            stderrExcerpt = result.stderrExcerpt,
        )
    }
}
