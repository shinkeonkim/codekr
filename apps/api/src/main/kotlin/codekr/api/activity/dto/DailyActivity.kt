package codekr.api.activity.dto

import java.time.LocalDate

/**
 * 하루치 활동. [count] 는 그래프 강도에 쓰이고, 스트릭에는 "있었는가"만 쓰인다.
 *
 * [solvedCount] 는 **그날 정답 판정을 받은 서로 다른 문제 수**다 (#133).
 * "그날 처음 맞힌 문제" 가 아니다 — 그 값은 과거에 의존해서, 재채점으로 옛 판정이
 * 뒤집히면 그 뒤 모든 날의 숫자가 달라진다. 하루치만 다시 세는 갱신 방식으로는
 * 그것을 따라갈 수 없다.
 *
 * 그래서 화면에서도 "새로 푼" 이 아니라 **"맞힌 문제"** 라고 부른다.
 */
data class DailyActivity(val date: LocalDate, val count: Int, val solvedCount: Int = 0)
