package codekr.api.user.badge

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/**
 * 배지 요청 제한 (#475).
 *
 * **로그인이 없다.** 배지는 README 안에서 열려야 하므로 토큰을 요구할 수 없고, 그래서
 * 누구나 무제한으로 부를 수 있는 자리가 된다. 사람 기준으로 셀 수 없으니 **주소(IP)로**
 * 센다.
 *
 * ## 1분 고정 창이다
 *
 * 정교한 방식(토큰 버킷·슬라이딩 윈도)을 쓰지 않았다. 여기서 막고 싶은 것은 **한 곳이
 * 우리 서버를 계속 두드리는 것**이고, 그것은 거친 계산으로도 잡힌다. 창이 바뀌는 순간
 * 두 배가 지나갈 수 있지만, 그 두 배도 상한의 두 배일 뿐이다.
 *
 * ## 첫 방어선은 캐시다
 *
 * 이 제한이 주 방어가 아니다. 배지는 10분 캐시를 달고 나가므로 README 를 여는 사람
 * 대부분은 **프록시가 받는다.** 여기까지 오는 것은 캐시를 지나온 요청이고, 그 수가
 * 갑자기 크다는 것은 사람이 읽고 있다는 뜻이 아니다.
 */
@Component
class BadgeRateLimiter(private val redis: StringRedisTemplate) {

    /** 넘으면 거짓. 창 하나에 이만큼까지 받는다. */
    fun allow(clientKey: String): Boolean {
        val window = Instant.now().epochSecond / WINDOW_SECONDS
        val key = "$PREFIX:$clientKey:$window"
        val count = redis.opsForValue().increment(key) ?: return true
        if (count == 1L) {
            // 창이 지나면 스스로 사라지게 한다 — 지우는 일을 따로 두지 않는다.
            redis.expire(key, Duration.ofSeconds(WINDOW_SECONDS * 2))
        }
        return count <= LIMIT_PER_WINDOW
    }

    private companion object {
        const val PREFIX = "codekr:badge:rate"
        const val WINDOW_SECONDS = 60L

        /**
         * 1분에 120회.
         *
         * 캐시를 지나온 요청만 여기 오므로 사람이 읽어서 이 수가 나오기는 어렵다.
         * 반대로 너무 낮게 잡으면 회사·학교처럼 **주소 하나 뒤에 여럿이 있는 곳**이
         * 먼저 막힌다 — 그쪽이 우리가 닿고 싶은 자리다.
         */
        const val LIMIT_PER_WINDOW = 120
    }
}
