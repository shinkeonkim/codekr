package codekr.api.ranking.badge

import codekr.api.problem.entity.ProblemCategory

/**
 * 뱃지 (#58).
 *
 * **행동 기반으로만 준다.** 점수 기반 뱃지는 실력 티어와 같은 말을 두 번 하는 것이다.
 *
 * 코드만 DB 에 저장하고 이름·설명은 여기서 갖는다 — 문구를 고치려고 마이그레이션하지 않는다.
 * 뜻이 달라지면 새 코드를 쓴다.
 */
enum class Badge(val code: String, val label: String, val description: String) {
    FIRST_ACCEPT("FIRST_ACCEPT", "첫 정답", "처음으로 문제를 맞혔습니다"),
    STREAK_7("STREAK_7", "일주일 연속", "7일 연속으로 문제를 풀었습니다"),
    STREAK_30("STREAK_30", "한 달 연속", "30일 연속으로 문제를 풀었습니다"),
    FIRST_SOLVER("FIRST_SOLVER", "최초 해결자", "새로 올라온 문제를 가장 먼저 맞혔습니다"),
    ;

    companion object {
        /** 카테고리 뱃지에 필요한 문제 수. */
        const val CATEGORY_THRESHOLD = 10

        private const val CATEGORY_PREFIX = "CATEGORY_10_"

        fun categoryCode(category: ProblemCategory) = "$CATEGORY_PREFIX${category.name}"

        /**
         * 코드를 사람이 읽을 이름으로 옮긴다.
         *
         * 모르는 코드가 와도 죽지 않는다 — 옛 코드가 남아 있어도 화면이 깨지면 안 된다.
         */
        fun describe(code: String): BadgeInfo {
            entries.firstOrNull { it.code == code }?.let { return BadgeInfo(code, it.label, it.description) }

            if (code.startsWith(CATEGORY_PREFIX)) {
                val category = runCatching {
                    ProblemCategory.valueOf(code.removePrefix(CATEGORY_PREFIX))
                }.getOrNull()
                if (category != null) {
                    return BadgeInfo(
                        code,
                        "${category.label} $CATEGORY_THRESHOLD 문제",
                        "${category.label} 문제를 $CATEGORY_THRESHOLD 개 맞혔습니다",
                    )
                }
            }
            return BadgeInfo(code, code, "")
        }
    }
}

data class BadgeInfo(val code: String, val label: String, val description: String)
