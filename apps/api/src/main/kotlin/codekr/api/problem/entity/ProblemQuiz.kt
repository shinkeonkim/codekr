package codekr.api.problem.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 답을 어떻게 받는가 (#650).
 *
 * **`MULTIPLE` 은 정확히 일치해야 정답이다.** 부분 점수는 여기서 정하지 않는다 —
 * #473 이 그 자리이고, 먼저 정하면 두 곳에서 다르게 정해진다.
 */
enum class QuizAnswerType(val label: String) {
    SINGLE("객관식 (하나)"),
    MULTIPLE("객관식 (여럿)"),
    SHORT("단답"),
    ;

    val usesChoices: Boolean get() = this != SHORT
}

/**
 * 개념 퀴즈의 설정 (#650).
 *
 * **실행기를 쓰지 않는 첫 유형이다.** 채점이 값 비교라 api 가 즉시 하고, 그래서
 * 시간·메모리 제한도 런타임도 테스트케이스도 없다.
 */
@Entity
@Table(name = "problem_quiz_specs")
class ProblemQuizSpec(
    @Id
    @Column(name = "problem_id")
    val problemId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "answer_type", nullable = false, length = 20)
    var answerType: QuizAnswerType,

    /**
     * 채점이 끝난 뒤에만 보여 준다.
     *
     * **퀴즈에는 이것이 판정만큼 중요하다.** 코드 문제는 틀린 이유가 판정과 출력에
     * 드러나지만, 4지선다에서 "틀렸습니다" 만으로는 아무것도 배우지 못한다.
     */
    @Column(name = "explanation")
    var explanation: String? = null,

    /**
     * 단답을 견줄 때 대소문자를 무시할지. **문제마다 다르다** — `TCP`/`tcp` 는 같지만
     * `chmod` 의 `X` 와 `x` 는 다른 것을 가리킨다.
     */
    @Column(name = "ignore_case", nullable = false)
    var ignoreCase: Boolean = true,

    /** 공백을 무시할지. `set uid` 와 `setuid` 를 가를 문제가 있다. */
    @Column(name = "ignore_whitespace", nullable = false)
    var ignoreWhitespace: Boolean = true,
)

/**
 * 객관식 보기 (#650).
 *
 * **[correct] 는 사용자에게 나가지 않는다.** 응답 DTO 가 담지 않는 것이 유일한 방어라,
 * 새 DTO 를 만들 때마다 그것을 기억해야 한다 — `QuizChoiceResponse` 하나만 쓰게 두어
 * 기억할 자리를 하나로 모은다.
 */
@Entity
@Table(name = "problem_quiz_choices")
class ProblemQuizChoice(
    @Column(name = "problem_id", nullable = false)
    val problemId: Long,

    @Column(nullable = false)
    val seq: Int,

    @Column(nullable = false)
    val content: String,

    @Column(nullable = false)
    val correct: Boolean = false,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}

/**
 * 단답으로 받아 줄 답 (#650).
 *
 * **동의어를 여기서 푼다.** `TCP` 와 `전송 제어 프로토콜` 을 정규화로 잇는 방법은 없다 —
 * 출제자가 받아 줄 것을 적는 편이 정직하고, 무엇을 받아 주는지가 데이터에 남는다.
 */
@Entity
@Table(name = "problem_quiz_answers")
class ProblemQuizAnswer(
    @Column(name = "problem_id", nullable = false)
    val problemId: Long,

    @Column(nullable = false)
    val seq: Int,

    @Column(nullable = false)
    val content: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}
