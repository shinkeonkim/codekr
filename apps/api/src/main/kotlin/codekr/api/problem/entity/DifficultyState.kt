package codekr.api.problem.entity

/**
 * 난이도가 매겨진 상태인가 (#195).
 *
 * **셋을 나누는 이유는 뜻이 다르기 때문이다.**
 *
 * - `UNRATED` 는 "아직 모른다" — 사람들이 풀어 봐야 아는 값이라 곧 실제 난이도가 붙는다
 * - `NO_RATE` 는 "해당 없다" — 튜토리얼·설문형처럼 난이도로 표현되지 않는 문제다.
 *   **영영 점수를 주지 않는 것이 맞다**
 *
 * 둘 다 **문제 수에는 세고 점수에는 넣지 않는다.** 푼 사람 입장에서 "푼 문제" 인 것은
 * 사실이고, 점수는 난이도에서 나오는데 그 근거가 없을 뿐이다.
 */
enum class DifficultyState(val label: String) {
    RATED("난이도 있음"),
    UNRATED("미평가"),
    NO_RATE("평가 안 함"),
    ;

    /** 점수를 주는가. 난이도가 없으면 점수를 매길 근거가 없다. */
    val scored: Boolean get() = this == RATED
}
