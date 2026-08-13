package codekr.api.user

import codekr.api.auth.security.JwtTokenProvider
import codekr.api.support.IntegrationTestBase
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import codekr.api.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 프로필 소개 문구 (#310).
 *
 * **프로필에 그 사람이 직접 쓴 것이 한 글자도 없었다** — 닉네임·아바타 말고는 전부
 * 서버가 센 숫자다. 여기서 확인하는 것은 쓴 것이 남에게 보이는지, 그리고 지워야 할
 * 자리(탈퇴·어드민)에서 실제로 지워지는지다.
 */
class ProfileBioIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider

    private lateinit var token: String
    private lateinit var adminToken: String
    private var userId: Long = 0

    @BeforeEach
    fun setUp() {
        val user = userRepository.save(User("solver@codekr.dev", "x", "풀이왕", setOf(UserRole.USER)))
        userId = user.id
        token = tokenProvider.issueAccessToken(user)
        adminToken = tokenProvider.issueAccessToken(
            userRepository.save(User("admin@codekr.dev", "x", "관리자", setOf(UserRole.ADMIN))),
        )
    }

    @Test
    fun `쓴 소개가 남에게 보인다`() {
        write("""{"bio":"알고리즘 공부 중입니다."}""")

        mockMvc.perform(profile())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.bio").value("알고리즘 공부 중입니다."))
    }

    @Test
    fun `안 쓴 사람은 자리가 아예 없다`() {
        // 빈 문자열로 내리면 화면이 빈 칸을 그리고, 그것은 "안 쓴 사람" 이 아니라
        // "고장 난 화면" 으로 보인다.
        mockMvc.perform(profile())
            .andExpect(jsonPath("$.bio").doesNotExist())
    }

    @Test
    fun `빈 문자열을 보내면 지운다`() {
        write("""{"bio":"한 줄 소개"}""")
        write("""{"bio":"   "}""")

        mockMvc.perform(profile())
            .andExpect(jsonPath("$.bio").doesNotExist())
    }

    @Test
    fun `보내지 않은 항목은 그대로 둔다`() {
        // 설정 변경(#104)과 같은 규칙이다 — 전체를 보내게 하면 옛 화면이 새 항목을 지운다.
        write("""{"bio":"그대로"}""")
        write("""{}""")

        mockMvc.perform(profile())
            .andExpect(jsonPath("$.bio").value("그대로"))
    }

    @Test
    fun `백자를 넘기면 막힌다`() {
        mockMvc.perform(
            patch("/api/v1/users/me/profile").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"bio":"${"가".repeat(101)}"}"""),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `줄바꿈은 살리고 줄 끝 공백은 다듬는다`() {
        write("""{"bio":"첫 줄   \n  둘째 줄  "}""")

        mockMvc.perform(profile())
            .andExpect(jsonPath("$.bio").value("첫 줄\n  둘째 줄"))
    }

    @Test
    fun `탈퇴하면 소개도 지워진다`() {
        // **가장 개인적인 내용이 들어갈 곳**이다 (#140).
        write("""{"bio":"연락처는 여기로"}""")

        mockMvc.perform(delete("/api/v1/users/me").header("Authorization", "Bearer $token"))
            .andExpect(status().isNoContent)

        val withdrawn = userRepository.findById(userId).orElseThrow()
        org.junit.jupiter.api.Assertions.assertNull(withdrawn.bio)
    }

    @Test
    fun `어드민이 지울 수 있고 지운 내용이 기록에 남는다`() {
        write("""{"bio":"광고 문구입니다"}""")

        mockMvc.perform(
            delete("/api/v1/admin/users/$userId/bio").param("reason", "광고")
                .header("Authorization", "Bearer $adminToken"),
        ).andExpect(status().isNoContent)

        mockMvc.perform(profile())
            .andExpect(jsonPath("$.bio").doesNotExist())

        val superuser = tokenProvider.issueAccessToken(
            userRepository.save(User("root@codekr.dev", "x", "최고관리자", setOf(UserRole.SUPERUSER))),
        )
        mockMvc.perform(
            get("/api/v1/admin/audit-logs").param("targetUserId", userId.toString())
                .header("Authorization", "Bearer $superuser"),
        )
            .andExpect(jsonPath("$.content[0].action").value("BIO_CLEAR"))
            // 지운 뒤에는 무엇이 적혀 있었는지 알 길이 없다.
            .andExpect(jsonPath("$.content[0].detail").value("광고 문구입니다"))
    }

    @Test
    fun `사유 없이는 지울 수 없다`() {
        write("""{"bio":"광고 문구입니다"}""")

        mockMvc.perform(
            delete("/api/v1/admin/users/$userId/bio").header("Authorization", "Bearer $adminToken"),
        ).andExpect(status().isBadRequest)

        mockMvc.perform(profile())
            .andExpect(jsonPath("$.bio").value("광고 문구입니다"))
    }

    /**
     * 남의 눈으로 본 프로필.
     *
     * **로그인이 필요하다** — 이 화면은 `@AuthenticatedApi` 다. 이슈 본문은 "로그인
     * 없이도 열리는 화면" 이라고 적었지만 지금은 그렇지 않다(#333 으로 남겼다).
     * 그래서 어드민 토큰으로, 즉 본인이 아닌 사람의 눈으로 본다.
     */
    private fun profile() = get("/api/v1/users/풀이왕").header("Authorization", "Bearer $adminToken")

    private fun write(body: String) {
        mockMvc.perform(
            patch("/api/v1/users/me/profile").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        ).andExpect(status().isOk)
    }
}
