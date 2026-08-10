package codekr.api.auth.security

import codekr.api.config.properties.JwtProperties
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Date

@Component
class JwtTokenProvider(private val properties: JwtProperties) {

    private val key = Keys.hmacShaKeyFor(properties.secret.toByteArray(StandardCharsets.UTF_8))

    fun issueAccessToken(user: User): String = issue(user, TokenType.ACCESS, properties.accessTtlSeconds)

    fun issueRefreshToken(user: User): String = issue(user, TokenType.REFRESH, properties.refreshTtlSeconds)

    /** 유효하지 않거나 만료된 토큰이면 null 을 반환한다 (예외를 흘리지 않는다). */
    fun parse(token: String, expected: TokenType): AuthPrincipal? = try {
        val claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload
        if (claims["type"] != expected.name) {
            null
        } else {
            AuthPrincipal(
                userId = claims.subject.toLong(),
                email = claims["email"] as String,
                role = UserRole.valueOf(claims["role"] as String),
            )
        }
    } catch (e: JwtException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun issue(user: User, type: TokenType, ttlSeconds: Long): String {
        val now = Instant.now()
        return Jwts.builder()
            .subject(user.id.toString())
            .claim("email", user.email)
            .claim("role", user.role.name)
            .claim("type", type.name)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(ttlSeconds)))
            .signWith(key)
            .compact()
    }
}
