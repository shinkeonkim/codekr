package codekr.api.problem.quiz

import codekr.api.problem.entity.ProblemQuizChoice
import codekr.api.problem.entity.ProblemQuizSpec
import codekr.api.problem.entity.QuizAnswerType
import jakarta.validation.constraints.Size

/**
 * 화면이 문제를 그리는 데 필요한 것 (#650).
 *
 * **정답이 여기 없다.** `ProblemQuizChoice.correct` 를 담지 않는 것이 유일한 방어이고,
 * 새면 개발자 도구를 여는 것만으로 문제가 무너진다. 해설도 없다 — 채점한 뒤에만 준다.
 *
 * 문제 상세(`ProblemDetailResponse`)가 히든 테스트케이스를 담지 않는 것과 같은 규칙이다:
 * **담을 자리를 만들지 않으면 실수로도 새지 않는다.**
 */
data class QuizViewResponse(
    val answerType: QuizAnswerType,
    val answerTypeLabel: String,
    val choices: List<QuizChoiceResponse>,
) {
    companion object {
        fun of(spec: ProblemQuizSpec, choices: List<ProblemQuizChoice>) = QuizViewResponse(
            answerType = spec.answerType,
            answerTypeLabel = spec.answerType.label,
            choices = if (spec.answerType.usesChoices) {
                choices.sortedBy { it.seq }.map { QuizChoiceResponse(it.seq, it.content) }
            } else {
                emptyList()
            },
        )
    }
}

/** 보기 하나. **`correct` 를 담지 않는다.** */
data class QuizChoiceResponse(val seq: Int, val content: String)

/**
 * 사용자가 낸 답 (#650).
 *
 * 객관식과 단답을 한 요청 타입으로 받는다 — 유형은 문제가 정하고, 화면은 그에 맞는
 * 칸 하나만 채운다. 나뉘어 있으면 화면이 어느 경로로 보낼지 다시 판단해야 한다.
 */
data class QuizSubmitRequest(
    /** 고른 보기의 `seq` 들. 단답이면 비어 있다. */
    @field:Size(max = MAX_SELECTED, message = "고를 수 있는 보기 수를 넘었습니다.")
    val selected: Set<Int> = emptySet(),
    /** 적은 글자. 객관식이면 `null` 이다. */
    @field:Size(max = MAX_TEXT, message = "답이 너무 깁니다.")
    val text: String? = null,
) {
    companion object {
        const val MAX_SELECTED = 50
        const val MAX_TEXT = 200
    }
}

/**
 * 채점 결과 (#650).
 *
 * **해설은 여기서 처음 나간다.** 문제를 받을 때 함께 주면 풀기 전에 답이 보인다.
 */
data class QuizSubmitResponse(
    val submissionId: Long,
    val correct: Boolean,
    val explanation: String?,
)
