package codekr.api.ranking.badge

/**
 * 뱃지 규칙이 도는 순간 (#202, 설계는 #200 §3).
 *
 * **이벤트가 없으면 그 순간을 조건으로 쓸 수 없다.** 지금은 둘로 시작하고, 나머지는
 * 그 기능이 붙을 때 함께 낸다 — 다만 **이름과 실려 오는 값의 모양은 기획에서 정했다.**
 */
enum class BadgeEventType {
    PROBLEM_ACCEPTED,
    STREAK_UPDATED,
}

/**
 * 평가에 필요한 것 전부.
 *
 * `problemId` 같은 값은 **이벤트 지표**(#200 §4.1)가 쓴다 — `is_first_solver` 는
 * "방금 맞힌 그 문제에 대해 내가 처음인가" 라 이벤트 없이는 계산되지 않는다.
 */
data class BadgeEvent(
    val type: BadgeEventType,
    val userId: Long,
    val problemId: Long? = null,
)
