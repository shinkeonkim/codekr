package codekr.api.problem.admin.dto

import codekr.api.problem.entity.ProblemQuizAnswer
import codekr.api.problem.entity.ProblemQuizChoice
import codekr.api.problem.entity.ProblemQuizSpec
import codekr.api.problem.entity.QuizAnswerType

/**
 * 어드민이 편집할 퀴즈 (#650).
 *
 * **푸는 사람이 받는 `QuizViewResponse` 와 다른 타입이다.** 여기에는 정답 표시와
 * 해설이 있고 저기에는 없다 — 한 타입을 두 곳에서 쓰면 "이번에는 빼야 한다" 를
 * 부르는 쪽마다 기억해야 하고, 한 번 잊으면 문제가 무너진다.
 */
data class QuizSpecResponse(
    val answerType: QuizAnswerType,
    val explanation: String?,
    val choices: List<QuizChoiceResponse>,
    val answers: List<String>,
    val ignoreCase: Boolean,
    val ignoreWhitespace: Boolean,
) {
    data class QuizChoiceResponse(val content: String, val correct: Boolean)

    companion object {
        fun of(
            spec: ProblemQuizSpec,
            choices: List<ProblemQuizChoice>,
            answers: List<ProblemQuizAnswer>,
        ) = QuizSpecResponse(
            answerType = spec.answerType,
            explanation = spec.explanation,
            choices = choices.sortedBy { it.seq }.map { QuizChoiceResponse(it.content, it.correct) },
            answers = answers.sortedBy { it.seq }.map { it.content },
            ignoreCase = spec.ignoreCase,
            ignoreWhitespace = spec.ignoreWhitespace,
        )
    }
}
