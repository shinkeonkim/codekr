package codekr.api.activity

import codekr.api.activity.service.StreakCalculator
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals

class StreakCalculatorTest {

    private val today = LocalDate.of(2026, 8, 10)

    private fun days(vararg dates: String) = dates.map(LocalDate::parse).toSet()

    @Test
    fun `오늘까지 이어지면 오늘을 포함해 센다`() {
        val active = days("2026-08-08", "2026-08-09", "2026-08-10")

        assertEquals(3, StreakCalculator.current(active, today))
    }

    @Test
    fun `오늘 활동이 없어도 어제까지 이어져 있으면 유지된다`() {
        // 아침에 접속한 사용자에게 사실과 다른 좌절을 주지 않기 위한 규칙이다.
        val active = days("2026-08-08", "2026-08-09")

        assertEquals(2, StreakCalculator.current(active, today))
    }

    @Test
    fun `이틀 이상 비면 현재 스트릭은 0 이다`() {
        val active = days("2026-08-06", "2026-08-07")

        assertEquals(0, StreakCalculator.current(active, today))
    }

    @Test
    fun `활동이 없으면 0 이다`() {
        assertEquals(0, StreakCalculator.current(emptySet(), today))
        assertEquals(0, StreakCalculator.longest(emptySet()))
    }

    @Test
    fun `최장 스트릭은 기간 안에서 가장 긴 연속 구간이다`() {
        val active = days(
            "2026-07-01", "2026-07-02", "2026-07-03", "2026-07-04",
            "2026-07-10",
            "2026-08-08", "2026-08-09",
        )

        assertEquals(4, StreakCalculator.longest(active))
    }

    @Test
    fun `하루만 활동해도 최장 스트릭은 1 이다`() {
        assertEquals(1, StreakCalculator.longest(days("2026-07-10")))
    }

    @Test
    fun `달과 해를 넘어가도 연속으로 센다`() {
        val active = days("2025-12-30", "2025-12-31", "2026-01-01", "2026-01-02")

        assertEquals(4, StreakCalculator.longest(active))
    }
}
