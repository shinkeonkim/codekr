package codekr.api.problem.entity

/**
 * 문제 난이도. 브론즈 5단계(가장 쉬움)부터 루비 1단계(가장 어려움)까지 30단계다.
 *
 * DB 에는 정렬과 범위 검색이 쉬운 정수 [level] 로 저장하고, 이 enum 은 그 값을 읽기 쉬운
 * 이름으로 옮기는 역할만 한다.
 */
enum class Difficulty {
    BRONZE_5, BRONZE_4, BRONZE_3, BRONZE_2, BRONZE_1,
    SILVER_5, SILVER_4, SILVER_3, SILVER_2, SILVER_1,
    GOLD_5, GOLD_4, GOLD_3, GOLD_2, GOLD_1,
    PLATINUM_5, PLATINUM_4, PLATINUM_3, PLATINUM_2, PLATINUM_1,
    DIAMOND_5, DIAMOND_4, DIAMOND_3, DIAMOND_2, DIAMOND_1,
    RUBY_5, RUBY_4, RUBY_3, RUBY_2, RUBY_1,
    ;

    /** 1(브론즈 5) ~ 30(루비 1). 숫자가 클수록 어렵다. */
    val level: Int get() = ordinal + 1

    val tier: DifficultyTier get() = DifficultyTier.ofLevel(level)

    /** 티어 안에서의 단계. 5가 가장 쉽고 1이 가장 어렵다. */
    val step: Int get() = DifficultyTier.STEPS_PER_TIER - (level - tier.firstLevel)

    /** 화면에 그대로 쓰는 표기 (예: "골드 4"). */
    val label: String get() = "${tier.label} $step"

    companion object {
        fun ofLevel(level: Int): Difficulty =
            entries.getOrNull(level - 1)
                ?: throw IllegalArgumentException("난이도 레벨 범위를 벗어났습니다: $level")
    }
}
