package codekr.api.problem.admin.service

import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.problem.admin.dto.VerificationResponse
import codekr.api.problem.entity.Problem
import codekr.api.problem.repository.ProblemRepository
import codekr.api.queue.JudgeJobFactory
import codekr.api.queue.QueuePublisher
import codekr.api.queue.JudgePriority
import codekr.api.queue.message.JudgeJobMessage
import codekr.api.submission.entity.Submission
import codekr.api.submission.entity.SubmissionKind
import codekr.api.submission.repository.SubmissionRepository
import codekr.api.submission.repository.SubmissionTestcaseResultRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 어드민이 등록한 정답 코드로 문제의 전체 테스트케이스를 검증한다 (#39).
 *
 * 채점 파이프라인을 새로 만들지 않고 **사용자 제출과 같은 큐를 쓴다.** 대신 제출 종류를
 * [SubmissionKind.SOLUTION_VERIFICATION] 으로 구분해, 사용자에게 보이는 모든 경로에서 걸러낸다.
 */
@Service
@Transactional(readOnly = true)
class SolutionVerificationService(
    private val problemRepository: ProblemRepository,
    private val submissionRepository: SubmissionRepository,
    private val resultRepository: SubmissionTestcaseResultRepository,
    private val queuePublisher: QueuePublisher,
    private val judgeJobFactory: JudgeJobFactory,
) {

    /** 검증을 시작한다. 이미 진행 중이던 검증이 있으면 새 실행이 그것을 대체한다. */
    @Transactional
    fun verify(problemId: Long, adminUserId: Long): VerificationResponse {
        val problem = problemRepository.findByIdAndDeletedAtIsNull(problemId)
            ?: throw ApiException(ErrorCode.PROBLEM_NOT_FOUND)

        if (!problem.hasSolution) throw ApiException(ErrorCode.SOLUTION_REQUIRED)
        if (problem.testcases.isEmpty()) throw ApiException(ErrorCode.TESTCASE_REQUIRED)

        val submission = submissionRepository.save(
            Submission(
                userId = adminUserId,
                problemId = problem.id,
                runtimeId = requireNotNull(problem.solutionRuntimeId),
                sourceCode = requireNotNull(problem.solutionSourceCode),
                totalCount = problem.judgeUnitCount,
                kind = SubmissionKind.SOLUTION_VERIFICATION,
            ),
        )
        problem.startVerification(submission.id)

        queuePublisher.publishJudgeJob(
            judgeJobFactory.of(submission, problem),
            JudgePriority.of(submission.kind, problem),
        )
        return requireNotNull(VerificationResponse.of(problem, submission, emptyList()))
    }

    /** 마지막 검증의 진행/결과. 한 번도 검증하지 않았으면 null 이다. */
    fun findLatest(problem: Problem): VerificationResponse? {
        val submissionId = problem.verificationSubmissionId ?: return null
        val submission = submissionRepository.findByIdAndDeletedAtIsNull(submissionId) ?: return null

        return VerificationResponse.of(
            problem = problem,
            submission = submission,
            results = resultRepository.findBySubmissionIdOrderBySeqAsc(submissionId),
        )
    }
}
