package codekr.api.activity.dto

import java.time.LocalDate

/** 하루치 활동. [count] 는 그래프 강도에 쓰이고, 스트릭에는 "있었는가"만 쓰인다. */
data class DailyActivity(val date: LocalDate, val count: Int)
