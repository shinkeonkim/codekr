package codekr.api.problem.quiz

import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.problem.entity.Problem
import codekr.api.problem.entity.ProblemKind
import codekr.api.problem.service.ProblemService
import codekr.api.queue.QueuePublisher
import codekr.api.queue.message.JudgeEventMessage
import codekr.api.submission.entity.Submission
import codekr.api.submission.entity.SubmissionVisibility
import codekr.api.submission.entity.Verdict
import codekr.api.submission.repository.SubmissionRepository
import codekr.api.submission.service.SubmissionCooldown
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * 퀴즈를 받아 그 자리에서 채점한다 (#650).
 *
 * **실행기를 쓰지 않는 첫 경로다.** 그래서 `SubmissionService.submit` 을 쓸 수 없다 —
 * 거기는 런타임을 요구하고 소스 코드가 비면 막는다. 대신 결과는 **채점기가 낸 것과
 * 같은 길**(`codekr:events`)로 흘려보내, 점수·활동·뱃지·통계·실시간 중계가 새 경로
 * 없이 그대로 붙게 한다.
 *
 * **트랜잭션을 걸지 않는다.** 쓰기가 제출 행 하나뿐이라 묶을 것이 없고, 무엇보다
 * 이벤트를 커밋 전에 발행하면 **받는 쪽이 그 제출을 못 찾는다** — 채점기가 느려서
 * 드러나지 않을 뿐, 즉시 채점에서는 매번 부딪힌다.
 */
@Service
class QuizSubmissionService(
    private val problemService: ProblemService,
    private val specRepository: ProblemQuizSpecRepository,
    private val choiceRepository: ProblemQuizChoiceRepository,
    private val answerRepository: ProblemQuizAnswerRepository,
    private val submissionRepository: SubmissionRepository,
    private val queuePublisher: QueuePublisher,
) {

    fun submit(key: String, userId: Long, request: QuizSubmitRequest): QuizSubmitResponse {
        val problem = requireQuiz(key)
        val spec = specRepository.findById(problem.id).orElse(null)
            // 유형은 QUIZ 인데 설정이 없다 — 출제자가 덜 채운 것이다. 짐작해 채점하지 않는다.
            ?: throw ApiException(ErrorCode.VALIDATION_ERROR, "아직 준비되지 않은 문제입니다.")

        if (request.selected.isEmpty() && request.text.isNullOrBlank()) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, QuizGrader.EMPTY_ANSWER)
        }
        SubmissionCooldown.require(
            lastSubmittedAt = submissionRepository
                .findFirstByUserIdAndProblemIdAndContestIdIsNullAndDeletedAtIsNullOrderByIdDesc(userId, problem.id)
                ?.createdAt,
            cooldown = SubmissionCooldown.DEFAULT,
            now = Instant.now(),
        )

        val correct = QuizGrader.grade(
            spec = spec,
            choices = choiceRepository.findByProblemIdOrderBySeqAsc(problem.id),
            answers = answerRepository.findByProblemIdOrderBySeqAsc(problem.id),
            selected = request.selected,
            text = request.text,
        )

        val submission = submissionRepository.save(
            Submission(
                userId = userId,
                problemId = problem.id,
                runtimeId = RUNTIME_ID,
                sourceCode = describe(request),
                totalCount = 1,
            ).apply {
                /*
                    **퀴즈 답은 공개하지 않는다.**

                    다른 유형에서 `sourceCode` 는 풀이이고, 공개는 서로에게 배울 거리가
                    된다 (#33). 여기서는 그것이 **정답 그 자체**다 — 맞힌 사람의 제출을
                    공개로 두면 문제가 무너진다.

                    사용자 기본값(#104)도 따르지 않는다. 기본을 공개로 둔 사람이 퀴즈를
                    풀었다고 답이 새면 안 된다.
                */
                changeVisibility(SubmissionVisibility.PRIVATE)
            },
        )

        publish(submission.id, correct)
        return QuizSubmitResponse(
            submissionId = submission.id,
            correct = correct,
            // **해설은 여기서 처음 나간다.** 문제를 받을 때 주면 풀기 전에 답이 보인다.
            explanation = spec.explanation,
        )
    }

    private fun requireQuiz(key: String): Problem {
        val problem = problemService.requirePublished(key)
        if (problem.problemKind != ProblemKind.QUIZ) {
            // 코드로 낼 문제에 답만 보내는 경로가 열려 있으면 안 된다.
            throw ApiException(ErrorCode.VALIDATION_ERROR, "이 문제는 답을 골라 내는 문제가 아닙니다.")
        }
        return problem
    }

    /**
     * 낸 답을 사람이 읽을 수 있게 남긴다.
     *
     * 제출 목록·상세가 `sourceCode` 를 그린다. 비워 두면 **자기가 무엇을 냈는지**
     * 다시 볼 수 없어, 틀린 뒤에 해설을 읽어도 무엇과 견줄지가 없다.
     */
    private fun describe(request: QuizSubmitRequest): String =
        request.text?.takeIf { it.isNotBlank() }
            ?: request.selected.sorted().joinToString(", ") { "$it 번" }

    /** 진행과 완료를 함께 보낸다 — 화면이 기다리는 순서가 그것이다 (`JUDGING` → `COMPLETED`). */
    private fun publish(submissionId: Long, correct: Boolean) {
        queuePublisher.publishJudgeEvent(
            JudgeEventMessage(
                type = JudgeEventMessage.TYPE_JUDGING,
                submissionId = submissionId,
                totalCount = 1,
            ),
        )
        queuePublisher.publishJudgeEvent(
            JudgeEventMessage(
                type = JudgeEventMessage.TYPE_COMPLETED,
                submissionId = submissionId,
                verdict = (if (correct) Verdict.ACCEPTED else Verdict.WRONG_ANSWER).name,
                passedCount = if (correct) 1 else 0,
                totalCount = 1,
            ),
        )
    }

    companion object {
        /**
         * 실행 환경 자리에 남기는 이름.
         *
         * `submissions.runtime_id` 는 NOT NULL 이고 목록·상세가 그대로 그린다.
         * 런타임 목록에는 없는 이름인데, **읽는 쪽이 목록을 뒤지지 않아서** 괜찮다 —
         * 그리는 것은 문자열 그대로다.
         */
        const val RUNTIME_ID = "quiz"
    }
}
