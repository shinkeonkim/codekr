package codekr.api.submission.dto

import codekr.api.problem.entity.Problem
import codekr.api.submission.entity.Submission
import codekr.api.submission.entity.SubmissionStatus
import codekr.api.submission.entity.Verdict
import java.time.Instant

data class SubmissionSummaryResponse(
    val id: Long,
    val problemSlug: String,
    val problemTitle: String,
    val runtimeId: String,
    val status: SubmissionStatus,
    val verdict: Verdict?,
    val passedCount: Int,
    val totalCount: Int,
    val createdAt: Instant,
) {
    companion object {
        /** 문제가 삭제됐어도 제출 이력은 남아야 하므로, 삭제된 문제 정보도 그대로 보여준다. */
        fun of(submission: Submission, problem: Problem?) = SubmissionSummaryResponse(
            id = submission.id,
            problemSlug = problem?.slug.orEmpty(),
            problemTitle = problem?.title ?: "(삭제된 문제)",
            runtimeId = submission.runtimeId,
            status = submission.status,
            verdict = submission.verdict,
            passedCount = submission.passedCount,
            totalCount = submission.totalCount,
            createdAt = submission.createdAt,
        )
    }
}
