package codekr.api.feedback

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * 비회원 신고의 속도 제한 (#611).
 *
 * ## 왜 IP 인가
 *
 * **로그인하지 않은 사람을 가릴 수 있는 것이 그것뿐이다.** 캡차는 의존성이 하나 늘고
 * 바깥으로 나간다 — 로그인이 안 돼서 신고하려는 사람에게 **또 하나의 외부 서비스를
 * 통과하게 하는 것**은 이 기능의 목적과 어긋난다.
 *
 * **같은 학교·회사에서 여러 명이 들어오면 애먼 사람이 막힌다.** 그래서 한도를 넉넉히
 * 둔다 — 이 통로로 들어올 신고는 "가입이 안 됩니다" 류라 한 사람이 한두 번 넣고 만다.
 * 한 시간에 다섯 건이면 실제 사용자가 걸릴 일이 거의 없고, 스팸은 그 자리에서 멈춘다.
 *
 * 배지(#475)가 같은 방식으로 IP 를 센다 — **규칙을 새로 만들지 않았다.**
 */
@Component
class AnonymousFeedbackRateLimiter(private val redis: StringRedisTemplate) {

    /** 이 시간 창에서 [LIMIT] 건까지. 창이 지나면 저절로 풀린다. */
    fun allow(clientKey: String): Boolean {
        val window = System.currentTimeMillis() / WINDOW.toMillis()
        val key = "$PREFIX:$clientKey:$window"
        val count = redis.opsForValue().increment(key)
        // **Redis 가 답하지 않으면 막지 않는다.** 신고를 받는 것이 세는 것보다 중요하다.
            ?: return true
        if (count == 1L) redis.expire(key, WINDOW.multipliedBy(2))
        return count <= LIMIT
    }

    private companion object {
        const val PREFIX = "codekr:feedback:anon"
        const val LIMIT = 5L
        val WINDOW: Duration = Duration.ofHours(1)
    }
}
