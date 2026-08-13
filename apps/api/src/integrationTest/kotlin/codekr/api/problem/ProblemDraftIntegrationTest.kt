package codekr.api.problem

import codekr.api.auth.security.JwtTokenProvider
import codekr.api.support.IntegrationTestBase
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import codekr.api.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 초안 도구가 꺼져 있을 때 (#230).
 *
 * **시험 환경에는 키가 없다.** 그래서 여기서 확인하는 것은 "만들어 준다" 가 아니라
 * **"없을 때 무엇이 되는가"** 다 — 그쪽이 오히려 매일 도는 상태다.
 */
class ProblemDraftIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider

    @Test
    fun `키가 없으면 그 기능은 없는 것이다`() {
        /*
          403 이 아니라 **404** 다 (#115 와 같은 규칙). "권한이 없다" 는 그 기능이
          존재한다는 말이기도 하다. 그리고 이 응답이 곧 **손으로 채우는 길은 그대로**
          라는 뜻이다 — 도구가 없어도 폼은 돈다.
        */
        mockMvc.perform(
            post("/api/v1/admin/problems/draft")
                .header("Authorization", "Bearer ${token(UserRole.PROBLEM_SETTER)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"statement":"두 정수 A와 B가 주어진다."}"""),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("FEATURE_DISABLED"))
    }

    @Test
    fun `출제자가 아니면 부를 수 없다`() {
        mockMvc.perform(
            post("/api/v1/admin/problems/draft")
                .header("Authorization", "Bearer ${token(UserRole.USER)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"statement":"두 정수"}"""),
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `비로그인은 여기까지 오지도 못한다`() {
        mockMvc.perform(
            post("/api/v1/admin/problems/draft")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"statement":"두 정수"}"""),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `빈 지문은 부르기 전에 막는다`() {
        // 바깥으로 나가고 돈이 드는 호출이다. 보낼 것이 없으면 보내지 않는다.
        mockMvc.perform(
            post("/api/v1/admin/problems/draft")
                .header("Authorization", "Bearer ${token(UserRole.PROBLEM_SETTER)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"statement":"   "}"""),
        ).andExpect(status().isBadRequest)
    }

    private fun token(role: UserRole): String {
        val email = "draft-${role.name.lowercase()}@codekr.dev"
        val user = userRepository.findByEmail(email)
            ?: userRepository.save(User(email, "x", "초안${role.name}", setOf(UserRole.USER, role)))
        return tokenProvider.issueAccessToken(user)
    }
}
