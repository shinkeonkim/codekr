package codekr.api.activity.dto

import java.time.LocalDate

data class ActivityResponse(
    val from: LocalDate,
    val to: LocalDate,
    /** 활동이 있었던 날만 담는다. 없는 날은 0 으로 그린다. */
    val days: List<DailyActivity>,
    val totalCount: Int,
    val activeDayCount: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    /** 하루 경계를 정한 시간대. 화면이 같은 기준으로 오늘을 계산할 수 있게 함께 내린다. */
    val timeZone: String,
)
