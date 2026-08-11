package codekr.api.submission.service

import codekr.api.activity.service.ActivityRecorder
import codekr.api.queue.message.JudgeEventMessage
import codekr.api.ranking.service.ScoreRecorder
import codekr.api.rejudge.service.RejudgeCompletion
import codekr.api.submission.entity.SubmissionKind
import codekr.api.submission.entity.SubmissionTestcaseResult
import codekr.api.submission.repository.SubmissionRepository
import codekr.api.submission.repository.SubmissionTestcaseResultRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 채점 이벤트를 DB 에 반영한다. PostgreSQL 에 쓰는 주체는 이 애플리케이션뿐이다 (ADR-0004).
 *
 * 같은 이벤트가 두 번 도착할 수 있으므로 모든 반영은 멱등해야 한다.
 */
@Component
class JudgeResultRecorder(
    private val submissionRepository: SubmissionRepository,
    private val resultRepository: SubmissionTestcaseResultRepository,
    private val rejudgeCompletion: RejudgeCompletion,
    private val activityRecorder: ActivityRecorder,
    private val scoreRecorder: ScoreRecorder,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun record(event: JudgeEventMessage) {
        val submission = submissionRepository.findById(event.submissionId).orElse(null)
        if (submission == null) {
            log.warn("알 수 없는 제출의 채점 이벤트를 받았습니다: {}", event.submissionId)
            return
        }

        when (event.type) {
            JudgeEventMessage.TYPE_JUDGING -> submission.markJudging(event.totalCount)
            JudgeEventMessage.TYPE_TESTCASE -> upsertTestcaseResult(event)
            JudgeEventMessage.TYPE_COMPLETED -> {
                submission.complete(event.toOutcome())
                // 집계는 제출을 SQL 로 다시 읽어 계산한다. 방금 바꾼 판정이 아직 DB 에
                // 없으면 직전 판정으로 집계된다 — 그래서 여기서 먼저 밀어낸다.
                submissionRepository.flush()

                var scoreDelta = 0
                // 활동·점수 집계는 제출 테이블과 분리돼 있다 (#105, #57).
                // 결과가 확정되는 여기서 반영한다.
                if (submission.kind == SubmissionKind.USER) {
                    activityRecorder.recordCompletion(submission.userId, submission.createdAt)
                    scoreDelta = scoreRecorder.record(submission.userId, submission.problemId)
                }
                // 재채점이었으면 판정이 바뀌었는지 보고 알린다 (#107).
                // 여기서 부르는 이유: 결과가 확정되는 유일한 지점이다.
                if (submission.isRejudging) rejudgeCompletion.completeOne(submission.id, scoreDelta)
            }
            else -> log.warn("알 수 없는 채점 이벤트 유형: {}", event.type)
        }
    }

    /** 같은 순번이 다시 오면 갱신한다 — 유니크 제약과 함께 재전달을 멱등하게 만든다. */
    private fun upsertTestcaseResult(event: JudgeEventMessage) {
        val verdict = event.verdictOrSystemError()
        val existing = resultRepository.findBySubmissionIdAndSeq(event.submissionId, event.seq)

        if (existing != null) {
            existing.update(verdict, event.runtimeMs, event.memoryKb, event.stderrExcerpt)
            return
        }
        resultRepository.save(
            SubmissionTestcaseResult(
                submissionId = event.submissionId,
                seq = event.seq,
                verdict = verdict,
                runtimeMs = event.runtimeMs,
                memoryKb = event.memoryKb,
                stderrExcerpt = event.stderrExcerpt,
            ),
        )
    }
}
