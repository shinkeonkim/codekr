package codekr.api.problem

import codekr.api.problem.entity.ProblemQuizAnswer
import codekr.api.problem.entity.ProblemQuizChoice
import codekr.api.problem.entity.ProblemQuizSpec
import codekr.api.problem.entity.QuizAnswerType
import codekr.api.problem.quiz.QuizGrader
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 퀴즈 채점 (#650).
 *
 * **여기는 틀리면 조용히 틀리는 자리다.** 채점이 어긋나도 오류가 나지 않고, 사용자는
 * 자기가 틀린 줄 안다. #605 에서 Redis 문제 하나가 **틀린 답도 `ACCEPTED`** 였던 것이
 * 같은 종류였고, 그때 배운 것은 **정답만 돌려 보면 안 된다**는 것이다.
 *
 * 그래서 여기서도 갈래마다 **맞는 답과 틀린 답을 둘 다** 넣는다.
 */
class QuizGraderTest {

    private fun spec(
        type: QuizAnswerType,
        ignoreCase: Boolean = true,
        ignoreWhitespace: Boolean = true,
    ) = ProblemQuizSpec(1, type, ignoreCase = ignoreCase, ignoreWhitespace = ignoreWhitespace)

    private fun choices(vararg correctSeq: Int) =
        (1..4).map { ProblemQuizChoice(1, it, "보기 $it", it in correctSeq) }

    private fun answers(vararg values: String) =
        values.mapIndexed { index, value -> ProblemQuizAnswer(1, index + 1, value) }

    private fun grade(
        spec: ProblemQuizSpec,
        selected: Set<Int> = emptySet(),
        text: String? = null,
        choices: List<ProblemQuizChoice> = choices(2),
        answers: List<ProblemQuizAnswer> = emptyList(),
    ) = QuizGrader.grade(spec, choices, answers, selected, text)

    @Test
    fun `객관식은 정답 하나를 고르면 맞는다`() {
        assertTrue(grade(spec(QuizAnswerType.SINGLE), selected = setOf(2)))
        assertFalse(grade(spec(QuizAnswerType.SINGLE), selected = setOf(3)))
    }

    /**
     * **하나를 더 고른 것도 오답이다.**
     *
     * "맞는 것을 하나라도 고르면 정답" 이면 **전부 고르는 것이 최적 전략**이 된다.
     * 그러면 문제가 묻는 것이 지식이 아니라 그 사실을 아는지가 된다.
     */
    @Test
    fun `여럿 고르는 문제는 정확히 일치해야 맞는다`() {
        val multiple = spec(QuizAnswerType.MULTIPLE)
        val twoCorrect = choices(1, 3)

        assertTrue(grade(multiple, selected = setOf(1, 3), choices = twoCorrect))
        // 덜 고른 것
        assertFalse(grade(multiple, selected = setOf(1), choices = twoCorrect))
        // 더 고른 것 — **전부 고르기가 통하면 안 된다**
        assertFalse(grade(multiple, selected = setOf(1, 2, 3, 4), choices = twoCorrect))
        // 순서는 상관없다
        assertTrue(grade(multiple, selected = setOf(3, 1), choices = twoCorrect))
    }

    @Test
    fun `아무것도 안 고르면 오답이다`() {
        assertFalse(grade(spec(QuizAnswerType.SINGLE), selected = emptySet()))
    }

    /** 화면을 거치지 않고 부르면 없는 번호를 보낼 수 있다. */
    @Test
    fun `없는 보기 번호는 정답이 되지 못한다`() {
        assertFalse(grade(spec(QuizAnswerType.SINGLE), selected = setOf(99)))
        // 정답에 없는 번호를 끼워 넣어도 안 된다.
        assertFalse(grade(spec(QuizAnswerType.SINGLE), selected = setOf(2, 99)))
    }

    @Test
    fun `단답은 받아 주기로 한 답 중 하나면 맞는다`() {
        val short = spec(QuizAnswerType.SHORT)
        val accepted = answers("TCP", "전송 제어 프로토콜")

        assertTrue(grade(short, text = "TCP", answers = accepted))
        assertTrue(grade(short, text = "전송 제어 프로토콜", answers = accepted))
        assertFalse(grade(short, text = "UDP", answers = accepted))
    }

    /** 붙여 넣다 딸려 온 공백 때문에 틀리는 것은 문제가 묻는 것이 아니다. */
    @Test
    fun `양 끝 공백은 규칙과 무관하게 지운다`() {
        val strict = spec(QuizAnswerType.SHORT, ignoreCase = false, ignoreWhitespace = false)

        assertTrue(grade(strict, text = "  TCP \n", answers = answers("TCP")))
    }

    @Test
    fun `대소문자와 공백을 무시할지는 문제가 정한다`() {
        val loose = spec(QuizAnswerType.SHORT)
        assertTrue(grade(loose, text = "t c p", answers = answers("TCP")))

        // 대소문자가 뜻을 가르는 문제가 있다 (`chmod` 의 X 와 x).
        val caseSensitive = spec(QuizAnswerType.SHORT, ignoreCase = false)
        assertFalse(grade(caseSensitive, text = "tcp", answers = answers("TCP")))
        assertTrue(grade(caseSensitive, text = "TCP", answers = answers("TCP")))

        val spaceSensitive = spec(QuizAnswerType.SHORT, ignoreWhitespace = false)
        assertFalse(grade(spaceSensitive, text = "set uid", answers = answers("setuid")))
    }

    @Test
    fun `단답을 비우면 오답이다`() {
        val short = spec(QuizAnswerType.SHORT)
        assertFalse(grade(short, text = null, answers = answers("TCP")))
        assertFalse(grade(short, text = "   ", answers = answers("TCP")))
    }

    /**
     * **받아 줄 답이 하나도 없으면 아무것도 맞지 않는다.**
     *
     * 출제자의 실수인데, 여기서 "다 맞음" 으로 떨어지면 그 실수가 정답률로 덮인다.
     */
    @Test
    fun `받아 줄 답이 없으면 무엇을 적어도 오답이다`() {
        assertFalse(grade(spec(QuizAnswerType.SHORT), text = "무엇이든", answers = emptyList()))
    }
}
