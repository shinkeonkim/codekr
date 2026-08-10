package codekr.api.problem.entity

/**
 * 난이도 티어. solved.ac 체계를 따른다 — 각 티어는 5단계에서 1단계로 갈수록 어렵다.
 *
 * [firstLevel] 은 그 티어의 가장 쉬운 단계(5단계)가 갖는 전체 레벨 값이다.
 */
enum class DifficultyTier(val label: String, val firstLevel: Int) {
    BRONZE("브론즈", 1),
    SILVER("실버", 6),
    GOLD("골드", 11),
    PLATINUM("플래티넘", 16),
    DIAMOND("다이아몬드", 21),
    RUBY("루비", 26),
    ;

    /** 이 티어가 차지하는 전체 레벨 구간 (1~30). */
    val levelRange: IntRange get() = firstLevel..(firstLevel + STEPS_PER_TIER - 1)

    companion object {
        const val STEPS_PER_TIER = 5

        fun ofLevel(level: Int): DifficultyTier =
            entries.firstOrNull { level in it.levelRange }
                ?: throw IllegalArgumentException("난이도 레벨 범위를 벗어났습니다: $level")
    }
}
