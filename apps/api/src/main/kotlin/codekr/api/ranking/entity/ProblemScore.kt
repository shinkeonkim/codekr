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
}
