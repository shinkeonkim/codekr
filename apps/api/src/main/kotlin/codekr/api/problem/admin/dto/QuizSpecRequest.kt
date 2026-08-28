package codekr.api.problem.admin.dto

import codekr.api.problem.entity.ProblemQuizAnswer
import codekr.api.problem.entity.ProblemQuizChoice
import codekr.api.problem.entity.ProblemQuizSpec
import codekr.api.problem.entity.QuizAnswerType
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** 퀴즈 문제 등록/수정 (#650). */
data class QuizSpecRequest(
    val answerType: QuizAnswerType,

    /**
     * 채점 뒤에 보여 줄 해설.
     *
     * **비워 둘 수 있지만 비우지 않는 편이 낫다.** 4지선다에서 "틀렸습니다" 만으로는
     * 아무것도 배우지 못한다 — 코드 문제와 달리 판정 자체가 설명이 되지 않는다.
     */
    @field:Size(max = MAX_EXPLANATION)
    val explanation: String? = null,

    /** 객관식 보기. `SHORT` 면 비어 있어야 한다 — 검증이 막는다. */
    @field:Valid
    @field:Size(max = MAX_CHOICES, message = "보기는 ${MAX_CHOICES}개까지 넣을 수 있습니다.")
    val choices: List<QuizChoiceRequest> = emptyList(),

    /** 단답으로 받아 줄 답. `SINGLE`·`MULTIPLE` 이면 비어 있어야 한다. */
    @field:Size(max = MAX_ANSWERS, message = "받아 줄 답은 ${MAX_ANSWERS}개까지 넣을 수 있습니다.")
    val answers: List<@NotBlank String> = emptyList(),

    /** 단답을 견줄 때의 규칙. 문제마다 다르다 (`TCP`/`tcp`, `chmod` 의 `X`/`x`). */
    val ignoreCase: Boolean = true,
    val ignoreWhitespace: Boolean = true,
) {
    fun toSpec(problemId: Long) = ProblemQuizSpec(
        problemId = problemId,
        answerType = answerType,
        explanation = explanation?.ifBlank { null },
        ignoreCase = ignoreCase,
        ignoreWhitespace = ignoreWhitespace,
    )

    /** 번호는 **서버가 매긴다** — 화면이 보낸 번호를 믿으면 빈 번호나 겹친 번호가 들어온다. */
    fun toChoices(problemId: Long) = choices.mapIndexed { index, choice ->
        ProblemQuizChoice(problemId, index + 1, choice.content, choice.correct)
    }

    fun toAnswers(problemId: Long) = answers.mapIndexed { index, answer ->
        ProblemQuizAnswer(problemId, index + 1, answer.trim())
    }

    companion object {
        const val MAX_EXPLANATION = 4_000
        const val MAX_CHOICES = 10
        const val MAX_ANSWERS = 20
    }
}

/** 보기 하나. **`correct` 는 사용자에게 나가지 않는다** — 응답 DTO 가 담지 않는다. */
data class QuizChoiceRequest(
    @field:NotBlank(message = "보기 내용이 필요합니다.")
    @field:Size(max = MAX_CONTENT)
    val content: String,
    val correct: Boolean = false,
) {
    companion object {
        const val MAX_CONTENT = 1_000
    }
}
