package codekr.api.auth.withdrawal

import codekr.api.config.properties.JwtProperties
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * 탈퇴한 계정의 토큰을 즉시 막는다 (#140).
 *
 * JWT 는 상태가 없어서, 발급된 토큰은 만료까지 그대로 통한다. 액세스 토큰 수명이
 * 한 시간이면 **탈퇴 후 한 시간 동안 계속 쓸 수 있다** — "발급된 토큰도 더 이상
 * 통하지 않는다" 는 요구를 어긴다.
 *
 * 그렇다고 요청마다 DB 에서 사용자 상태를 읽으면 모든 인증 요청에 조회가 하나 붙는다.
 * **Redis 에 탈퇴한 사용자 id 만 담고**, 액세스 토큰 수명만큼만 둔다 — 그 뒤에는
 * 토큰 자체가 만료되므로 더 기억할 이유가 없다.
 */
@Component
class WithdrawnTokenRegistry(
    private val redis: StringRedisTemplate,
    private val jwtProperties: JwtProperties,
) {

    fun revoke(userId: Long) {
        // 액세스 토큰이 살아 있는 동안만 기억하면 된다. 조금 넉넉하게 둔다.
        redis.opsForValue().set(keyOf(userId), "1", Duration.ofSeconds(jwtProperties.accessTtlSeconds + MARGIN))
    }

    fun isRevoked(userId: Long): Boolean = redis.hasKey(keyOf(userId))

    private fun keyOf(userId: Long) = "$PREFIX$userId"

    private companion object {
        const val PREFIX = "codekr:withdrawn:"

        /** 시계 차이와 발급 직전의 요청을 감안한 여유. */
        const val MARGIN = 60L
    }
}
