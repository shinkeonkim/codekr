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
    /**
     * 대회 제출이면 그 대회 (#463).
     *
     * **대회 전용 사건을 따로 두지 않는다.** 대회에서 맞힌 것도 문제를 맞힌 것이고,
     * `PROBLEM_ACCEPTED` 는 이미 그 자리에서 돈다 — 없던 것은 **규칙이 "대회였는가"
     * 를 볼 방법**뿐이었다. 사건에 실어 보내면 지표를 늘리는 것으로 끝난다.
     */
    val contestId: Long? = null,
)
