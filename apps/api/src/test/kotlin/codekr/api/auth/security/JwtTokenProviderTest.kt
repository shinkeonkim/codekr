package codekr.api.auth.security

import codekr.api.config.properties.JwtProperties
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class JwtTokenProviderTest {

    private val properties = JwtProperties(
        secret = "test-secret-key-for-unit-test-32-bytes-long",
        accessTtlSeconds = 60,
        refreshTtlSeconds = 120,
    )
    private val provider = JwtTokenProvider(properties)
    private val user = User(email = "user@codekr.dev", passwordHash = "hash", nickname = "코더", role = UserRole.ADMIN)

    @Test
    fun `액세스 토큰을 발급하고 다시 파싱한다`() {
        val token = provider.issueAccessToken(user)

        val principal = provider.parse(token, TokenType.ACCESS)

        assertNotNull(principal)
        assertEquals("user@codekr.dev", principal.email)
        assertEquals(UserRole.ADMIN, principal.role)
    }

    @Test
    fun `리프레시 토큰을 액세스 토큰으로 사용할 수 없다`() {
        val refreshToken = provider.issueRefreshToken(user)

        assertNull(provider.parse(refreshToken, TokenType.ACCESS))
    }

    @Test
    fun `변조되거나 형식이 잘못된 토큰은 null 을 반환한다`() {
        assertNull(provider.parse("not-a-jwt", TokenType.ACCESS))
        assertNull(provider.parse(provider.issueAccessToken(user) + "x", TokenType.ACCESS))
    }

    @Test
    fun `만료된 토큰은 null 을 반환한다`() {
        val expiredProvider = JwtTokenProvider(properties.copy(accessTtlSeconds = -1))

        assertNull(expiredProvider.parse(expiredProvider.issueAccessToken(user), TokenType.ACCESS))
    }
}
