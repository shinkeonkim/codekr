package codekr.api.problem.admin.dto

import codekr.api.problem.entity.Problem
import codekr.api.submission.dto.TestcaseResultResponse
import codekr.api.submission.entity.Submission
import codekr.api.submission.entity.SubmissionStatus
import codekr.api.submission.entity.SubmissionTestcaseResult
import codekr.api.submission.entity.Verdict

/** 정답 코드 검증의 진행/결과. 아직 한 번도 검증하지 않았으면 null 이다. */
data class VerificationResponse(
    val submissionId: Long,
    val status: SubmissionStatus,
    val verdict: Verdict?,
    val passedCount: Int,
    val totalCount: Int,
    val compileError: String?,
    /** 검증 이후 문제나 테스트케이스가 바뀌었으면 true — 결과를 믿을 수 없다. */
    val stale: Boolean,
    val results: List<TestcaseResultResponse>,
) {
    companion object {
        fun of(
            problem: Problem,
            submission: Submission?,
            results: List<SubmissionTestcaseResult>,
        ): VerificationResponse? {
            if (submission == null) return null
            return VerificationResponse(
                submissionId = submission.id,
                status = submission.status,
                verdict = submission.verdict,
                passedCount = submission.passedCount,
                totalCount = submission.totalCount,
                compileError = submission.compileError,
                stale = problem.isVerificationStale,
                results = results.map(TestcaseResultResponse::from),
            )
        }
    }
}
