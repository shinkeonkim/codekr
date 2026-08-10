package codekr.api.submission.dto

import codekr.api.problem.entity.Problem
import codekr.api.submission.entity.Submission
import codekr.api.submission.entity.SubmissionStatus
import codekr.api.submission.entity.SubmissionTestcaseResult
import codekr.api.submission.entity.Verdict
import java.time.Instant

data class SubmissionDetailResponse(
    val id: Long,
    val problemSlug: String,
    val problemTitle: String,
    val runtimeId: String,
    val status: SubmissionStatus,
    val verdict: Verdict?,
    val passedCount: Int,
    val totalCount: Int,
    val maxRuntimeMs: Int,
    val maxMemoryKb: Int,
    val compileError: String?,
    val sourceCode: String,
    val results: List<TestcaseResultResponse>,
    val createdAt: Instant,
) {
    companion object {
        fun of(
            submission: Submission,
            problem: Problem?,
            results: List<SubmissionTestcaseResult>,
        ) = SubmissionDetailResponse(
            id = submission.id,
            problemSlug = problem?.slug.orEmpty(),
            problemTitle = problem?.title ?: "(삭제된 문제)",
            runtimeId = submission.runtimeId,
            status = submission.status,
            verdict = submission.verdict,
            passedCount = submission.passedCount,
            totalCount = submission.totalCount,
            maxRuntimeMs = submission.maxRuntimeMs,
            maxMemoryKb = submission.maxMemoryKb,
            compileError = submission.compileError,
            sourceCode = submission.sourceCode,
            results = results.map(TestcaseResultResponse::from),
            createdAt = submission.createdAt,
        )
    }
}
