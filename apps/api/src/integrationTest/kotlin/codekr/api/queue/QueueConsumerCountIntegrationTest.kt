package codekr.api.queue

import codekr.api.queue.service.QueueMonitorService
import codekr.api.support.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration
import kotlin.test.assertEquals

/**
 * 큐 상태가 **살아 있는 소비자만** 센다 (#699).
 *
 * `XINFO GROUPS` 의 `consumers` 는 한 번이라도 등록된 이름을 전부 센다. 소비자 이름이
 * 파드 이름이라(#415) 배포마다 죽은 이름이 쌓이는데, 운영에서 **51개 중 최근 5분 안에
 * 읽은 것은 1개**였다. 그 값으로는 "0 이면 아무도 안 읽고 있다" 를 말할 수 없다 —
 * 워커가 전부 죽어도 51 이다.
 */
class QueueConsumerCountIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var redis: StringRedisTemplate

    private fun register(name: String) {
        val ops = redis.opsForStream<String, String>()
        runCatching { ops.createGroup(QueueKeys.JUDGE_STREAM_LOW, ReadOffset.from("0"), QueueKeys.JUDGE_GROUP) }
        // 읽어야 소비자로 등록된다. 비어 있어도 이름은 남는다 — 그것이 이 결함의 씨앗이다.
        ops.read(
            Consumer.from(QueueKeys.JUDGE_GROUP, name),
            StreamOffset.create(QueueKeys.JUDGE_STREAM_LOW, ReadOffset.lastConsumed()),
        )
    }

    private fun countWith(window: Duration): Long =
        QueueMonitorService(redis, window).status().streams
            .first { it.name == QueueKeys.JUDGE_STREAM_LOW }.consumers

    @Test
    fun `등록된 이름이 아니라 최근에 읽은 것을 센다`() {
        redis.delete(QueueKeys.JUDGE_STREAM_LOW)
        register("살아있는-워커")

        assertEquals(1L, countWith(Duration.ofMinutes(10)), "방금 읽은 소비자가 안 세어집니다")

        /*
            **기준 쪽을 움직여 같은 것을 본다.** 실제로 죽은 소비자를 만들려면 10분을
            기다려야 한다. 확인하려는 것은 세는 규칙이 `idle` 을 실제로 보는가이고,
            창을 0 으로 두면 아무도 그 안에 들지 못한다.

            고치기 전에는 이 값이 등록 수(1)였다 — `XINFO GROUPS` 의 `consumers` 는
            유휴 시간을 모른다.
        */
        assertEquals(0L, countWith(Duration.ZERO), "죽은 것으로 봐야 할 소비자가 세어집니다")
    }

    @Test
    fun `이름이 여럿 쌓여도 살아 있는 수만 센다`() {
        redis.delete(QueueKeys.JUDGE_STREAM_LOW)
        repeat(5) { register("워커-$it") }

        assertEquals(5L, countWith(Duration.ofMinutes(10)))
        assertEquals(0L, countWith(Duration.ZERO), "등록 수를 그대로 세고 있습니다")
    }
}
