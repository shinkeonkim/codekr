package codekr.api.contest.submission

import codekr.api.common.error.ApiException
import codekr.api.contest.audit.ContestAuditService
import jakarta.servlet.http.HttpServletRequest
import codekr.api.common.error.ErrorCode
import codekr.api.contest.entity.ContestProblemId
import codekr.api.contest.entity.ContestRegistrationId
import codekr.api.contest.repository.ContestProblemRepository
import codekr.api.contest.repository.ContestRegistrationRepository
import codekr.api.contest.repository.ContestRepository
import codekr.api.problem.repository.ProblemRepository
import codekr.api.queue.JudgeJobFactory
import codekr.api.queue.JudgePriority
import codekr.api.queue.QueuePublisher
import codekr.api.submission.dto.SubmitRequest
import codekr.api.submission.dto.SubmitResponse
import codekr.api.submission.entity.Submission
import codekr.api.submission.repository.SubmissionRepository
import codekr.api.config.properties.SubmissionProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

/**
 * 대회 제출 (#62).
 *
 * 평소 제출과 경로를 나눈 이유는 **마감 판정과 큐가 다르기** 때문이다. 한 경로에
 * 조건문으로 섞으면, 대회에만 필요한 규칙이 평소 제출에까지 영향을 준다.
 */
@Service
@Transactional
class ContestSubmissionService(
    private val contestRepository: ContestRepository,
    private val contestProblemRepository: ContestProblemRepository,
    private val registrationRepository: ContestRegistrationRepository,
    private val problemRepository: ProblemRepository,
    private val submissionRepository: SubmissionRepository,
    private val queuePublisher: QueuePublisher,
    private val judgeJobFactory: JudgeJobFactory,
    private val properties: SubmissionProperties,
    private val auditService: ContestAuditService,
) {

    fun submit(
        contestSlug: String,
        problemSlug: String,
        userId: Long,
        request: SubmitRequest,
        httpRequest: HttpServletRequest? = null,
    ): SubmitResponse {
        // **접수 시각을 먼저 잡는다.** 뒤의 검증에 걸리는 시간이 마감 판정에 섞이면,
        // 서버가 느린 날 마감 직전 제출이 부당하게 거부된다.
        val receivedAt = Instant.now()
        val contest = contestRepository.findBySlugAndDeletedAtIsNull(contestSlug)
            ?: throw ApiException(ErrorCode.CONTEST_NOT_FOUND)

        if (!contest.phaseAt(receivedAt).acceptsSubmission) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "지금은 제출을 받지 않습니다.")
        }
        if (!registrationRepository.existsById(ContestRegistrationId(contest.id, userId))) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "참가 등록을 먼저 해야 합니다.")
        }

        val problem = problemRepository.findBySlugAndDeletedAtIsNull(problemSlug)
            ?: throw ApiException(ErrorCode.PROBLEM_NOT_FOUND)
        val assignment = contestProblemRepository.findById(ContestProblemId(contest.id, problem.id))
            .orElseThrow { ApiException(ErrorCode.PROBLEM_NOT_FOUND) }
        // 제외된 문제는 더 낼 수 없다. 없어진 문제를 계속 푸는 것은 시간 낭비다 (#86).
        if (assignment.isExcluded) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "이 문제는 대회에서 제외되었습니다.")
        }

        if (request.sourceCode.toByteArray().size > properties.maxSourceCodeBytes) {
            throw ApiException(ErrorCode.SOURCE_CODE_TOO_LARGE)
        }
        requireCooldown(contest.id, userId, problem.id, receivedAt)

        val submission = submissionRepository.save(
            Submission(
                userId = userId,
                problemId = problem.id,
                runtimeId = request.runtimeId,
                sourceCode = request.sourceCode,
                totalCount = problem.judgeUnitCount,
                contestId = contest.id,
            ),
        )
        // 부정행위 의심이 생겼을 때 판단할 근거를 남긴다 (#148).
        // 대회가 끝난 뒤에 기록을 만들 수는 없다.
        httpRequest?.let { auditService.record(submission.id, contest.id, userId, it) }

        // 대회 제출은 전용 큐로 간다. 평소 제출을 밀어내지 않기 위함이다.
        queuePublisher.publishJudgeJob(judgeJobFactory.of(submission, problem), JudgePriority.CONTEST)
        return SubmitResponse.from(submission)
    }

    /**
     * 참가자·문제당 제출 간격 제한 (#62).
     *
     * **큐를 지키는 장치다.** 한 사람이 초당 여러 번 내면 그만큼 워커가 묶이고,
     * 그 대가는 같은 대회의 다른 참가자가 치른다. 문제마다 따로 세는 이유는
     * A 문제를 반복 제출하는 것이 B 문제 제출을 막으면 안 되기 때문이다.
     */
    private fun requireCooldown(contestId: Long, userId: Long, problemId: Long, now: Instant) {
        val last = submissionRepository
            .findFirstByContestIdAndUserIdAndProblemIdAndDeletedAtIsNullOrderByIdDesc(
                contestId,
                userId,
                problemId,
            ) ?: return

        val elapsed = Duration.between(last.createdAt, now)
        if (elapsed < COOLDOWN) {
            val remaining = COOLDOWN.minus(elapsed).seconds + 1
            throw ApiException(
                ErrorCode.VALIDATION_ERROR,
                "같은 문제는 ${COOLDOWN.seconds}초에 한 번 낼 수 있습니다. ${remaining}초 뒤에 다시 시도하십시오.",
            )
        }
    }

    private companion object {
        /**
         * 같은 문제에 다시 낼 수 있기까지의 시간.
         *
         * 20초는 "고치고 다시 내는" 정상 흐름을 막지 않으면서, 자동화된 반복 제출로
         * 큐를 채우는 것은 막는 선이다.
         */
        val COOLDOWN: Duration = Duration.ofSeconds(20)
    }
}
