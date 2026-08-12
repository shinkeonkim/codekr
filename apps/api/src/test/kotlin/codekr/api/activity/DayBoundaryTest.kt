package codekr.api.activity

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * 하루의 경계 (#241).
 *
 * **UTC 오후 3시부터 자정까지는 서울과 날짜가 하루 다르다.** 한국 자정 30분은 UTC 로
 * 전날 오후 3시 30분이다. 이 구간에서만 나는 어긋남은 시험이 하루 중 언제 도는지에
 * 따라 나타났다 사라졌다 한다 — 실제로 CI 가 그 시간대에만 빨갛게 됐다.
 *
 * 그래서 **시계를 얼려서** 그 순간을 골라 확인한다. 서버가 UTC 로 돌든 서울로 돌든
 * 답이 같아야 한다.
 */
class DayBoundaryTest {

    /** 서울 2026-08-12 00:30 = UTC 2026-08-11 15:30. 두 시간대의 날짜가 다른 순간이다. */
    private val midnightIshInSeoul = Instant.parse("2026-08-11T15:30:00Z")

    @Test
    fun `서울 자정 직후에도 오늘은 서울 날짜다`() {
        val clock = Clock.fixed(midnightIshInSeoul, ActivityPolicy.ZONE)

        assertEquals(LocalDate.of(2026, 8, 12), LocalDate.now(clock))
    }

    @Test
    fun `같은 순간을 UTC 로 보면 하루 전이다`() {
        // 이 차이가 문제의 전부다. 앱은 서울로 묻고, DB 세션은 접속한 JVM 의 기본
        // 시간대를 따라 UTC 로 답했다.
        val utcClock = Clock.fixed(midnightIshInSeoul, ZoneOffset.UTC)

        assertEquals(LocalDate.of(2026, 8, 11), LocalDate.now(utcClock))
    }

    @Test
    fun `어제는 서울 기준으로 하루 전이다`() {
        // 코드 열람 알림이 세는 "어제" 다 (#144). UTC 기준이면 8월 10일이 되어,
        // 실제로 어제 열람한 기록을 하나도 찾지 못한다.
        val clock = Clock.fixed(midnightIshInSeoul, ActivityPolicy.ZONE)

        assertEquals(LocalDate.of(2026, 8, 11), LocalDate.now(clock).minusDays(1))
    }

    @Test
    fun `UTC 오전에는 두 시간대의 날짜가 같다`() {
        // 이 구간에서는 시험이 통과한다 — 그래서 문제가 오래 드러나지 않았다.
        val morning = Instant.parse("2026-08-11T01:00:00Z")

        assertEquals(
            LocalDate.now(Clock.fixed(morning, ZoneOffset.UTC)),
            LocalDate.now(Clock.fixed(morning, ActivityPolicy.ZONE)),
        )
    }
}
