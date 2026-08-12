package codekr.api.activity.service

import codekr.api.activity.ActivityPolicy
import codekr.api.activity.dto.ActivityResponse
import codekr.api.activity.dto.Streaks
import codekr.api.activity.repository.UserDailyActivityRepository
import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class ActivityService(
    private val activityRepository: UserDailyActivityRepository,
    /** 오늘의 기준 (#241). 스트릭은 "오늘" 이 언제냐에 따라 끊기고 이어진다. */
    private val clock: Clock,
) {

    /**
     * 활동 그래프와 스트릭.
     *
     * [year] 를 주면 그 해 전체를 본다. from/to 와 함께 주면 year 가 이긴다 —
     * 화면은 둘 중 하나만 쓴다.
     */
    /**
     * 그 사용자의 스트릭. 프로필(#83)과 활동 그래프가 이 한 곳에서 받아 간다 (#117).
     *
     * 계산이 두 곳에 있으면 언젠가 어긋나고, 그때 사용자는 어느 쪽을 믿어야 할지 모른다.
     */
    fun streaksOf(userId: Long): Streaks {
        val activeDates = activityRepository.findActiveDates(userId)
        val today = LocalDate.now(clock)
        return Streaks(
            current = StreakCalculator.current(activeDates, today),
            longest = StreakCalculator.longest(activeDates),
        )
    }

    fun findActivity(userId: Long, from: LocalDate?, to: LocalDate?, year: Int? = null): ActivityResponse {
        val today = LocalDate.now(clock)
        val (start, end) = resolveRange(from, to, year, today)

        if (start.isAfter(end)) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "시작일이 종료일보다 늦습니다.")
        }
        if (start.plusDays(ActivityPolicy.MAX_RANGE_DAYS).isBefore(end)) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "조회 기간이 너무 깁니다.")
        }

        val days = activityRepository.findDailyCounts(userId, start, end)
        // 그래프는 조회 범위지만 스트릭은 전체 기간이다 (#81).
        val allActiveDates = activityRepository.findActiveDates(userId)
        val streaks = streaksOf(userId)

        return ActivityResponse(
            from = start,
            to = end,
            days = days,
            totalCount = days.sumOf { it.count },
            activeDayCount = days.size,
            currentStreak = streaks.current,
            longestStreak = streaks.longest,
            availableYears = availableYears(allActiveDates, today),
            timeZone = ActivityPolicy.ZONE.id,
        )
    }

    private fun resolveRange(
        from: LocalDate?,
        to: LocalDate?,
        year: Int?,
        today: LocalDate,
    ): Pair<LocalDate, LocalDate> {
        if (year != null) {
            if (year < MIN_YEAR || year > today.year + 1) {
                throw ApiException(ErrorCode.VALIDATION_ERROR, "조회할 수 없는 연도입니다.")
            }
            return LocalDate.of(year, 1, 1) to LocalDate.of(year, 12, 31)
        }
        val end = to ?: today
        return (from ?: end.minusDays(ActivityPolicy.DEFAULT_RANGE_DAYS - 1)) to end
    }

    /**
     * 활동이 있는 연도 + 올해. 최신 연도가 앞에 온다.
     *
     * 올해를 항상 넣는 이유는, 아직 아무것도 안 한 해에도 "올해" 를 보여줄 수 있어야 하기
     * 때문이다. 빈 그래프는 정보다 — 선택지가 아예 없는 것과 다르다.
     */
    private fun availableYears(activeDates: Set<LocalDate>, today: LocalDate): List<Int> =
        (activeDates.map { it.year } + today.year).distinct().sortedDescending()

    private companion object {
        /** 서비스 시작 이전 연도는 조회할 이유가 없다. */
        const val MIN_YEAR = 2020
    }
}
