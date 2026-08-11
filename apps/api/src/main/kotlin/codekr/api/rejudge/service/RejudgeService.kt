package codekr.api.rejudge.service

import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.notification.entity.NotificationCategory
import codekr.api.notification.service.NotificationService
import codekr.api.problem.repository.ProblemRepository
import codekr.api.queue.JudgePriority
import codekr.api.queue.JudgeJobFactory
import codekr.api.queue.QueuePublisher
import codekr.api.queue.message.JudgeJobMessage
import codekr.api.rejudge.dto.RejudgeResponse
import codekr.api.rejudge.entity.RejudgeBatch
import codekr.api.rejudge.repository.RejudgeBatchRepository
import codekr.api.submission.entity.SubmissionKind
import codekr.api.submission.repository.SubmissionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 재채점 (#107).
 *
 * **판정은 나빠지는 방향으로도 바뀐다.** 재채점 결과 답안이 틀렸다면 오답으로 바꾼다.
 * 그러지 않으면 잘못된 테스트케이스로 통과한 풀이가 남고, 그 위에 정답률(#84)과
 * 랭킹(#85)이 쌓이므로 그 위의 모든 숫자가 틀린다.
 */
@Service
@Transactional
class RejudgeService(
    private val problemRepository: ProblemRepository,
    private val submissionRepository: SubmissionRepository,
    private val batchRepository: RejudgeBatchRepository,
    private val queuePublisher: QueuePublisher,
    private val judgeJobFactory: JudgeJobFactory,
    private val notificationService: NotificationService,
) : RejudgeCompletion {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 문제의 사용자 제출을 모두 다시 채점한다.
     *
     * 정답 검증 제출(#39)은 대상이 아니다 — 어드민이 언제든 다시 돌릴 수 있고,
     * 그 결과로 사용자에게 알림이 갈 이유도 없다.
     */
    fun rejudgeProblem(problemId: Long, reason: String, requestedBy: Long): RejudgeResponse {
        val problem = problemRepository.findByIdAndDeletedAtIsNull(problemId)
            ?: throw ApiException(ErrorCode.PROBLEM_NOT_FOUND)
        if (problem.testcases.isEmpty()) throw ApiException(ErrorCode.TESTCASE_REQUIRED)

        val targets = submissionRepository.findByProblemIdAndKindAndDeletedAtIsNull(
            problemId,
            SubmissionKind.USER,
        )
        if (targets.isEmpty()) throw ApiException(ErrorCode.VALIDATION_ERROR, "재채점할 제출이 없습니다.")

        val batch = batchRepository.save(RejudgeBatch(problemId, reason, requestedBy, targets.size))

        targets.forEach { submission ->
            submission.startRejudge(batch.id)
            // 재채점은 사람이 기다리는 일이 아니다. 일반 제출을 밀어내지 않도록 낮은 등급으로 (#102).
            queuePublisher.publishJudgeJob(judgeJobFactory.of(submission, problem), JudgePriority.LOW)
        }

        log.info("재채점 시작: problemId={} 대상={} 이유={}", problemId, targets.size, reason)
        return RejudgeResponse.from(batch)
    }

    /**
     * 재채점 결과 하나를 마감한다. 판정이 바뀌었으면 그 사용자에게 알린다.
     *
     * **바뀌지 않은 사람에게는 보내지 않는다** — 소음이다.
     */
    override fun completeOne(submissionId: Long, scoreDelta: Int) {
        val submission = submissionRepository.findById(submissionId).orElse(null) ?: return
        val batchId = submission.rejudgeBatchId ?: return
        val batch = batchRepository.findById(batchId).orElse(null)

        val changed = submission.finishRejudge()
        if (!changed) return

        batch?.recordChange()
        notificationService.notify(
            userId = submission.userId,
            category = NotificationCategory.JUDGE,
            title = "재채점으로 판정이 바뀌었습니다",
            // 왜 바뀌었는지를 함께 담는다. 이유 없이 기록이 바뀌면 우리가 임의로 바꾼 것으로 읽힌다.
            body = notificationBody(batch?.reason, scoreDelta),
            link = "/submissions/${submission.id}",
        )
    }

    /**
     * 점수가 내려갔다면 그 사실도 함께 알린다 (#57).
     *
     * 알림을 따로 보내지 않는 이유: 점수가 내려가는 경우는 판정이 뒤집힌 경우뿐이라
     * 두 번 보내면 같은 일을 두 번 알리는 소음이 된다.
     */
    private fun notificationBody(reason: String?, scoreDelta: Int): String? {
        val scoreNote = if (scoreDelta < 0) "랭킹 점수가 ${-scoreDelta}점 내려갔습니다." else null
        return listOfNotNull(reason, scoreNote).joinToString(" ").ifBlank { null }
    }

    @Transactional(readOnly = true)
    fun findLatest(problemId: Long): RejudgeResponse? =
        batchRepository.findFirstByProblemIdOrderByIdDesc(problemId)?.let(RejudgeResponse::from)
}
