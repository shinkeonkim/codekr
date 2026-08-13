package codekr.api.admin

import codekr.api.auth.security.JwtTokenProvider
import codekr.api.support.IntegrationTestBase
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import codekr.api.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 꺼져 있을 때의 데이터 초기화 (#285, #303).
 *
 * **배포에서는 이 값이 주어지지 않는다** — 즉 운영은 언제나 이 시험과 같은 상태다.
 * #303 이 "어드민 화면에서 카드가 눌리지 않는 것을 확인한다" 고 적은 것을, 사람이 한 번
 * 눌러 보는 대신 **시험이 매번 확인**하게 한다.
 *
 * **403 이 아니라 404 다.** 403 은 "있는데 권한이 없다" 를 알려 준다 — 켜져 있는지
 * 여부까지 감춘다.
 */
@TestPropertySource(properties = ["codekr.data-reset.enabled=false"])
class DataResetDisabledIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider

    @Test
    fun `꺼져 있으면 최고 관리자가 눌러도 404 다`() {
        val admin = userRepository.save(
            User("root@codekr.dev", "x", "최고관리자", setOf(UserRole.USER, UserRole.SUPERUSER)),
        )
        val token = tokenProvider.issueAccessToken(admin)

        mockMvc.perform(
            post("/api/v1/admin/data/reset")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"confirmation":"문제와 제출을 모두 지웁니다"}"""),
        ).andExpect(status().isNotFound)
    }
}
