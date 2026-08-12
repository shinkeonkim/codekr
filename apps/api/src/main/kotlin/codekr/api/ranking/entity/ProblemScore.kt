package codekr.api.ranking.entity

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 문제 하나의 점수 (#57).
 *
 * 난이도 레벨(1~30)에 지수를 씌운다. 지수는 **티어 하나를 올리면 대략 3배**가 되도록
 * 잡았다 — 난이도 차이를 체감할 만큼 벌리되, 상위 티어 한 문제가 하위 전체를 압도하지
 * 않는 선이다.
 *
 * 오답에는 감점이 없다. 감점은 제출을 두려워하게 만들고, 학습 도구에서는 해롭다.
 */
object ProblemScore {

    private const val BASE = 10.0
    private const val GROWTH = 1.25

    fun of(difficultyLevel: Int): Int = (BASE * GROWTH.pow(difficultyLevel - 1)).roundToInt()

    /**
     * 같은 식의 SQL 판. 점수 행을 집합 단위로 다시 셀 때 쓴다.
     *
     * **식을 문자열로 세 곳에 적어 두고 있었다** (#194). 난이도가 바뀌어도 점수가 따라오지
     * 않는다는 것을 늦게 안 이유 중 하나가 그것이다 — 주석은 "지금의 난이도를 쓴다" 라고
     * 적혀 있는데, 그 식을 다시 돌리는 경로만 없었다.
     *
     * `p` 는 problems 를 가리키는 별칭이어야 한다.
     */
    const val SQL = "round($BASE * power($GROWTH, p.difficulty_level - 1))::int"
}
