package codekr.api.activity.service

import codekr.api.activity.ActivityPolicy
import codekr.api.activity.dto.ActivityResponse
import codekr.api.activity.repository.ActivityRepository
import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class ActivityService(private val activityRepository: ActivityRepository) {

    fun findActivity(userId: Long, from: LocalDate?, to: LocalDate?): ActivityResponse {
        val today = LocalDate.now(ActivityPolicy.ZONE)
        val end = to ?: today
        val start = from ?: end.minusDays(ActivityPolicy.DEFAULT_RANGE_DAYS - 1)

        if (start.isAfter(end)) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "시작일이 종료일보다 늦습니다.")
        }
        if (start.plusDays(ActivityPolicy.MAX_RANGE_DAYS).isBefore(end)) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "조회 기간이 너무 깁니다.")
        }

        val days = activityRepository.findDailyCounts(userId, start, end)
        val activeDays = days.map { it.date }.toSet()

        return ActivityResponse(
            from = start,
            to = end,
            days = days,
            totalCount = days.sumOf { it.count },
            activeDayCount = activeDays.size,
            currentStreak = StreakCalculator.current(activeDays, today),
            longestStreak = StreakCalculator.longest(activeDays),
            timeZone = ActivityPolicy.ZONE.id,
        )
    }
}
