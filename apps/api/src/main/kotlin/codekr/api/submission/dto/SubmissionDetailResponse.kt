package codekr.api.submission.dto

import codekr.api.problem.entity.Problem
import codekr.api.submission.entity.Submission
import codekr.api.submission.entity.SubmissionStatus
import codekr.api.submission.entity.SubmissionTestcaseResult
import codekr.api.submission.entity.SubmissionVisibility
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
    val visibility: SubmissionVisibility,
    /** 볼 권한이 없으면 null 이다. 필드를 비우는 것이 아니라 값 자체를 내리지 않는다. */
    val sourceCode: String?,
    val sourceVisible: Boolean,
    val nickname: String,
    val results: List<TestcaseResultResponse>,
    val createdAt: Instant,
) {
    companion object {
        fun of(
            submission: Submission,
            problem: Problem?,
            results: List<SubmissionTestcaseResult>,
            nickname: String,
            sourceVisible: Boolean,
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
            visibility = submission.visibility,
            sourceCode = submission.sourceCode.takeIf { sourceVisible },
            sourceVisible = sourceVisible,
            nickname = nickname,
            results = results.map(TestcaseResultResponse::from),
            createdAt = submission.createdAt,
        )
    }
}
