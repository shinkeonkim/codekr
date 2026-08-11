package codekr.api.submission.service

import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 같은 문제를 다시 낼 수 있기까지의 간격 (#189). */
class SubmissionCooldownTest {

    private val now: Instant = Instant.parse("2026-08-11T00:00:30Z")

    @Test
    fun `첫 제출은 언제나 통과한다`() {
        SubmissionCooldown.require(lastSubmittedAt = null, cooldown = Duration.ofSeconds(30), now = now)
    }

    @Test
    fun `간격이 지났으면 통과한다`() {
        SubmissionCooldown.require(now.minusSeconds(30), Duration.ofSeconds(30), now)
        SubmissionCooldown.require(now.minusSeconds(31), Duration.ofSeconds(30), now)
    }

    @Test
    fun `간격 안이면 429 로 거절한다`() {
        // 400 이 아니라 429 다 — 여기서 할 일은 고치는 것이 아니라 기다리는 것이다.
        val caught = assertFailsWith<ApiException> {
            SubmissionCooldown.require(now.minusSeconds(10), Duration.ofSeconds(30), now)
        }

        assertEquals(ErrorCode.SUBMISSION_TOO_FREQUENT, caught.errorCode)
        assertEquals("같은 문제는 30초에 한 번 낼 수 있습니다. 20초 뒤에 다시 시도하십시오.", caught.message)
    }

    @Test
    fun `남은 시간은 올림해서 알린다`() {
        // 0.2초 남았는데 "0초 뒤" 라고 하면 다시 눌러도 또 막힌다.
        val caught = assertFailsWith<ApiException> {
            SubmissionCooldown.require(now.minusMillis(29_800), Duration.ofSeconds(30), now)
        }

        assertTrue(caught.message.contains("1초 뒤"), caught.message)
    }

    @Test
    fun `대회가 하한 아래로 내려도 하한을 지킨다`() {
        assertEquals(Duration.ofSeconds(3), SubmissionCooldown.ofSeconds(0))
        assertEquals(Duration.ofSeconds(3), SubmissionCooldown.ofSeconds(1))
        assertEquals(Duration.ofSeconds(3), SubmissionCooldown.ofSeconds(3))
        assertEquals(Duration.ofSeconds(10), SubmissionCooldown.ofSeconds(10))
    }

    @Test
    fun `기본값이 정책과 같다`() {
        // 정책이 바뀌면 여기가 먼저 깨져야 한다.
        assertEquals(30L, SubmissionCooldown.DEFAULT.seconds)
        assertEquals(3L, SubmissionCooldown.MINIMUM.seconds)
    }
}
