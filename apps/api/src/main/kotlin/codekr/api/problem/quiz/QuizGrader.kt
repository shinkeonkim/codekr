package codekr.api.problem.quiz

import codekr.api.problem.entity.ProblemQuizAnswer
import codekr.api.problem.entity.ProblemQuizChoice
import codekr.api.problem.entity.ProblemQuizSpec
import codekr.api.problem.entity.QuizAnswerType

/**
 * 퀴즈를 채점한다 (#650).
 *
 * **순수 함수로 둔다.** 저장소도 시각도 보지 않으므로 단위 시험으로 전부 덮을 수 있고,
 * 그것이 중요한 이유는 여기가 **틀리면 조용히 틀리는** 자리이기 때문이다 — 채점이
 * 어긋나도 오류가 나지 않고, 사용자는 자기가 틀린 줄 안다.
 *
 * 이 저장소가 그 종류를 이미 겪었다: #605 에서 Redis 문제 하나가 **틀린 답도
 * `ACCEPTED`** 였고, 데이터가 우연히 같아서 정답만 돌려 보면 드러나지 않았다.
 */
object QuizGrader {

    /** 고른 것도 적은 것도 없으면 채점할 것이 없다. */
    const val EMPTY_ANSWER = "답을 고르거나 적어 주세요."

    /**
     * @param selected 고른 보기의 `seq` 들 (객관식)
     * @param text 적은 글자 (단답)
     */
    fun grade(
        spec: ProblemQuizSpec,
        choices: List<ProblemQuizChoice>,
        answers: List<ProblemQuizAnswer>,
        selected: Set<Int>,
        text: String?,
    ): Boolean = when (spec.answerType) {
        QuizAnswerType.SINGLE, QuizAnswerType.MULTIPLE -> gradeChoices(choices, selected)
        QuizAnswerType.SHORT -> gradeShort(spec, answers, text)
    }

    /**
     * **정확히 일치해야 정답이다.**
     *
     * 하나를 더 고른 것과 하나를 덜 고른 것을 둘 다 오답으로 본다 — 여럿 고르는 문제에서
     * "맞는 것을 하나라도 고르면 정답" 이면 **전부 고르는 것이 최적 전략**이 된다.
     */
    private fun gradeChoices(choices: List<ProblemQuizChoice>, selected: Set<Int>): Boolean {
        val correct = choices.filter { it.correct }.map { it.seq }.toSet()
        // 고를 수 있는 것만 센다. 없는 번호를 보내도 정답이 되면 안 된다.
        val existing = choices.map { it.seq }.toSet()
        return selected.isNotEmpty() && selected.all { it in existing } && selected == correct
    }

    private fun gradeShort(
        spec: ProblemQuizSpec,
        answers: List<ProblemQuizAnswer>,
        text: String?,
    ): Boolean {
        val given = normalize(spec, text ?: return false)
        if (given.isEmpty()) return false
        return answers.any { normalize(spec, it.content) == given }
    }

    /**
     * 견주기 전에 다듬는다.
     *
     * **양 끝 공백은 규칙과 무관하게 늘 지운다.** 붙여 넣다 딸려 온 공백 때문에 틀리는
     * 것은 문제가 묻는 것이 아니다. 가운데 공백을 지울지는 문제가 정한다.
     */
    private fun normalize(spec: ProblemQuizSpec, value: String): String {
        var result = value.trim()
        if (spec.ignoreWhitespace) result = result.replace(WHITESPACE, "")
        if (spec.ignoreCase) result = result.lowercase()
        return result
    }

    private val WHITESPACE = Regex("\\s+")
}
