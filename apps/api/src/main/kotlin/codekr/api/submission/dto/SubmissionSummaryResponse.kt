package codekr.api.submission.dto

import codekr.api.problem.entity.Problem
import codekr.api.submission.entity.Submission
import codekr.api.submission.entity.SubmissionStatus
import codekr.api.submission.entity.SubmissionVisibility
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
    /** 목록에서 실행 시간·메모리로 정렬할 수 있으므로 값도 함께 내려준다. */
    val maxRuntimeMs: Int,
    val maxMemoryKb: Int,
    val visibility: SubmissionVisibility,
    /** 목록에서 상세로 들어갔을 때 소스를 볼 수 있는지 미리 알려준다. */
    val sourceVisible: Boolean,
    val nickname: String,
    val createdAt: Instant,
) {
    companion object {
        /** 문제가 삭제됐어도 제출 이력은 남아야 하므로, 삭제된 문제 정보도 그대로 보여준다. */
        fun of(
            submission: Submission,
            problem: Problem?,
            nickname: String = "",
            sourceVisible: Boolean = false,
        ) = SubmissionSummaryResponse(
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
            visibility = submission.visibility,
            sourceVisible = sourceVisible,
            nickname = nickname,
            createdAt = submission.createdAt,
        )
    }
}
