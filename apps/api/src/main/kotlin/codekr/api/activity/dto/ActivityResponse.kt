package codekr.api.activity.dto

import java.time.LocalDate

data class ActivityResponse(
    val from: LocalDate,
    val to: LocalDate,
    /** 활동이 있었던 날만 담는다. 없는 날은 0 으로 그린다. */
    val days: List<DailyActivity>,
    /** 조회 범위 안의 제출 수. */
    val totalCount: Int,
    /** 조회 범위 안의 활동한 날 수. */
    val activeDayCount: Int,
    /**
     * 스트릭은 **조회 범위와 무관하게 전체 기간** 기준이다 (#81).
     *
     * 2026년만 보고 있다고 최장 기록이 2026년 안으로 잘리면 안 되고,
     * 12/31~1/1 로 이어진 연속이 연도 경계에서 끊겨 보이면 안 된다.
     */
    val currentStreak: Int,
    val longestStreak: Int,
    /** 활동이 있는 연도. 화면의 연도 선택지가 된다 — 가입 전 연도는 의미가 없다. */
    val availableYears: List<Int>,
    /** 하루 경계를 정한 시간대. 화면이 같은 기준으로 오늘을 계산할 수 있게 함께 내린다. */
    val timeZone: String,
)
