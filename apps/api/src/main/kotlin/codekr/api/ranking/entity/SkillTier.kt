package codekr.api.ranking.entity

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 실력 티어 (#58).
 *
 * **문제 난이도 티어와 이름은 같지만 다른 개념이다.** 화면에서는 "실력 티어"와
 * "문제 난이도"로 구분해 표기한다 — 골드 5 사용자와 골드 5 문제는 서로 다른 말이다.
 *
 * **강등은 없다.** 학습 도구에서 강등은 이탈 사유가 된다. 점수가 내려가도(재채점 등)
 * 티어는 도달했던 최고를 유지한다. 시즌제를 도입하면 그때 재검토한다.
 */
data class SkillTier(val level: Int, val name: String, val nextLevelScore: Int?) {

    companion object {
        const val MAX_LEVEL = 30

        private const val BASE = 50.0
        private const val GROWTH = 1.35

        private val GROUPS = listOf("브론즈", "실버", "골드", "플래티넘", "다이아몬드", "루비")

        /**
         * 레벨 [level] 에 닿는 데 필요한 점수.
         *
         * 한 단계마다 약 1.35배 — 티어 하나(5단계)를 올리면 약 4.5배다.
         * 문제 점수(1.25배)보다 가파른 이유는, 티어를 올리려면 **더 어려운 문제를 더 많이**
         * 풀어야 하기 때문이다. 같은 기울기면 문제 하나를 풀 때마다 티어가 올라간다.
         */
        fun scoreFor(level: Int): Int = when {
            level <= 1 -> 0
            else -> (BASE * GROWTH.pow(level - 2)).roundToInt()
        }

        /** 점수에 해당하는 티어. 아직 한 문제도 못 풀었으면 null — 브론즈 5 가 아니라 **티어가 없다**. */
        fun of(score: Int): SkillTier? {
            if (score <= 0) return null
            val level = (MAX_LEVEL downTo 1).first { score >= scoreFor(it) }
            return SkillTier(
                level = level,
                name = nameOf(level),
                nextLevelScore = if (level < MAX_LEVEL) scoreFor(level + 1) else null,
            )
        }

        /** 레벨 1~30 을 6단계 × 5스텝으로 읽는다 (#19 의 난이도 체계와 같은 눈금). */
        fun nameOf(level: Int): String {
            val group = GROUPS[(level - 1) / 5]
            val step = 5 - (level - 1) % 5
            return "$group $step"
        }
    }
}
