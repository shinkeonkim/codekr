package codekr.api.activity.service

import java.time.LocalDate

/**
 * 활동한 날짜 집합에서 스트릭을 센다.
 *
 * 날짜 집합만 받으므로 DB 도 시간대도 모른다 — 규칙만 검증하는 테스트를 쓸 수 있다.
 */
object StreakCalculator {

    /**
     * 현재 스트릭. **오늘 활동이 없어도 어제까지 이어져 있으면 유지**한다.
     *
     * 오늘 하루가 끝나기 전에 스트릭이 끊긴 것으로 보이면, 아침에 접속한 사용자에게
     * 사실과 다른 좌절을 준다.
     */
    fun current(activeDays: Set<LocalDate>, today: LocalDate): Int {
        val start = when {
            activeDays.contains(today) -> today
            activeDays.contains(today.minusDays(1)) -> today.minusDays(1)
            else -> return 0
        }

        var streak = 0
        var cursor = start
        while (activeDays.contains(cursor)) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    /** 조회 기간 안에서 가장 길게 이어진 연속 일수. */
    fun longest(activeDays: Set<LocalDate>): Int {
        var longest = 0
        for (day in activeDays) {
            // 연속 구간의 시작점에서만 세면 전체를 한 번씩만 훑는다.
            if (activeDays.contains(day.minusDays(1))) continue

            var length = 0
            var cursor = day
            while (activeDays.contains(cursor)) {
                length++
                cursor = cursor.plusDays(1)
            }
            longest = maxOf(longest, length)
        }
        return longest
    }
}
