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
import codekr.api.rejudge.dto.RejudgeStatusResponse
import codekr.api.rejudge.entity.RejudgeBatch
import codekr.api.rejudge.entity.RejudgeSubmissionResult
import codekr.api.rejudge.entity.UserRejudgeSummary
import codekr.api.rejudge.repository.RejudgeSubmissionResultRepository
import codekr.api.submission.entity.RejudgeTransition
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
    private val resultRepository: RejudgeSubmissionResultRepository,
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
     * 재채점 결과 하나를 마감한다 (#187).
     *
     * **전이를 전부 남긴다** — 바뀐 것만이 아니라 그대로인 것도. 남기지 않으면 "그때 무슨
     * 일이 있었나"에 답할 수 없고, 재채점 대상이었던 사람은 자기 결과가 확정된 것인지
     * 아직 도는 중인지 구분할 수 없다.
     *
     * **알림은 제출마다 보내지 않는다.** 한 사람이 같은 문제에 스무 번 냈으면 스무 번
     * 울린다. 배치가 끝났을 때 사람마다 한 번, 전이를 묶어서 보낸다.
     */
    override fun completeOne(submissionId: Long, scoreDelta: Int) {
        val submission = submissionRepository.findById(submissionId).orElse(null) ?: return
        val batchId = submission.rejudgeBatchId ?: return
        val batch = batchRepository.findById(batchId).orElse(null)

        val transition = submission.finishRejudge()
        resultRepository.record(
            batchId,
            RejudgeSubmissionResult(
                submissionId = submission.id,
                userId = submission.userId,
                previousVerdict = transition.previous,
                newVerdict = transition.current,
                scoreDelta = scoreDelta,
            ),
        )

        if (batch?.recordResult(transition.changed) == true) notifyAll(batch)
    }

    /** 배치가 끝났다. 대상이었던 사람마다 한 번씩 결과를 알린다. */
    private fun notifyAll(batch: RejudgeBatch) {
        resultRepository.summarize(batch.id).forEach { summary ->
            notificationService.notify(
                userId = summary.userId,
                category = NotificationCategory.JUDGE,
                title = if (summary.changed.isEmpty()) {
                    "재채점 결과: 판정이 그대로입니다"
                } else {
                    "재채점으로 판정이 바뀌었습니다"
                },
                body = notificationBody(batch.reason, summary),
                // 바뀐 것이 하나면 그 제출로, 여러 건이면 그 문제의 내 제출 목록으로 보낸다.
                link = summary.changed.singleOrNull()
                    ?.let { "/submissions/${it.submissionId}" }
                    ?: "/problems/${problemSlugOf(batch.problemId)}/submissions",
            )
        }
        log.info("재채점 마감: batchId={} 대상={} 변경={}", batch.id, batch.targetCount, batch.changedCount)
    }

    private fun problemSlugOf(problemId: Long): String =
        problemRepository.findById(problemId).map { it.slug }.orElse("")

    /**
     * 무엇이 어떻게 바뀌었는지를 문장으로 만든다 (#187).
     *
     * **전이를 그대로 적는다** — "바뀌었습니다" 만으로는 유리해진 것인지 불리해진 것인지
     * 알 수 없다. 점수 변화도 같은 알림에 담는다. 점수가 내려가는 경우는 판정이 뒤집힌
     * 경우뿐이라, 따로 보내면 같은 일을 두 번 알리는 소음이 된다.
     */
    private fun notificationBody(reason: String, summary: UserRejudgeSummary): String {
        val changes = summary.changed
            .groupingBy { RejudgeTransition(it.previousVerdict, it.newVerdict).describe() }
            .eachCount()
            .map { (transition, count) -> if (count == 1) transition else transition + " (" + count + "건)" }

        val outcome = if (changes.isEmpty()) {
            "다시 채점했지만 ${summary.results.size}건 모두 판정이 같습니다."
        } else {
            "판정이 바뀌었습니다: ${changes.joinToString(", ")}"
        }
        val scoreNote = when {
            summary.scoreDelta < 0 -> "랭킹 점수가 ${-summary.scoreDelta}점 내려갔습니다."
            summary.scoreDelta > 0 -> "랭킹 점수가 ${summary.scoreDelta}점 올랐습니다."
            else -> null
        }
        return listOfNotNull(reason, outcome, scoreNote).joinToString(" ")
    }

    @Transactional(readOnly = true)
    fun findLatest(problemId: Long): RejudgeResponse? =
        batchRepository.findFirstByProblemIdOrderByIdDesc(problemId)?.let(RejudgeResponse::from)

    /**
     * 누르기 전에 보여줄 것 (#219).
     *
     * 대상 수를 **실행할 때와 같은 조건으로** 센다. 다른 조건으로 세면 화면이 보여준 수와
     * 실제로 알림을 받는 사람 수가 달라진다.
     */
    @Transactional(readOnly = true)
    fun status(problemId: Long): RejudgeStatusResponse {
        if (!problemRepository.existsByIdAndDeletedAtIsNull(problemId)) {
            throw ApiException(ErrorCode.PROBLEM_NOT_FOUND)
        }
        return RejudgeStatusResponse(
            problemId = problemId,
            targetCount = submissionRepository
                .countByProblemIdAndKindAndDeletedAtIsNull(problemId, SubmissionKind.USER)
                .toInt(),
            latest = findLatest(problemId),
        )
    }
}
