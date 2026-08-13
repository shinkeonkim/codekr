package codekr.api.auth.security

import codekr.api.config.properties.JwtProperties
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * 발급된 토큰을 만료 전에 막는다 (#140, #315).
 *
 * **두 자리에서 쓴다** — 탈퇴(#140)와 비밀번호 재설정(#315)이다. 처음에는 탈퇴 전용
 * 이름이었는데, 재설정도 "지금 살아 있는 액세스 토큰을 끊어야 한다" 는 같은 요구를
 * 갖는다. 이름이 한 쓰임에 묶여 있으면 두 번째 쓰임이 자기 것을 새로 만든다.
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
class RevokedTokenRegistry(
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
        const val PREFIX = "codekr:revoked:"

        /** 시계 차이와 발급 직전의 요청을 감안한 여유. */
        const val MARGIN = 60L
    }
}
