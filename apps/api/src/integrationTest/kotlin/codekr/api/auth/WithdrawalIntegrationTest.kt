package codekr.api.auth

import codekr.api.auth.security.JwtTokenProvider
import codekr.api.support.IntegrationTestBase
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import codekr.api.user.entity.WithdrawnUser
import codekr.api.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 회원 탈퇴와 작성 기록 보존 (#140). */
class WithdrawalIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var jdbcClient: JdbcClient

    private var leaverId: Long = 0
    private lateinit var leaverToken: String
    private lateinit var adminToken: String
    private var postId: Long = 0

    @BeforeEach
    fun setUp() {
        val leaver = userRepository.save(User("leaver@codekr.dev", "x", "떠나는이", setOf(UserRole.USER)))
        leaverId = leaver.id
        leaverToken = tokenProvider.issueAccessToken(leaver)
        adminToken = tokenProvider.issueAccessToken(
            userRepository.save(User("admin@codekr.dev", "x", "관리자", setOf(UserRole.USER, UserRole.SUPERUSER))),
        )

        postId = jdbcClient.sql(
            "INSERT INTO posts (board, author_id, title, body) VALUES ('FREE', :id, '남을 글', '본문') RETURNING id",
        ).param("id", leaverId).query(Long::class.java).single()
        jdbcClient.sql(
            "INSERT INTO comments (post_id, author_id, body) VALUES (:postId, :id, '남을 댓글')",
        ).param("postId", postId).param("id", leaverId).update()
    }

    @Test
    fun `탈퇴해도 글과 댓글은 자리에 남는다`() {
        // 대화에서 한쪽 말만 사라지면 남은 말이 뜻을 잃는다.
        withdraw()

        mockMvc.perform(get("/api/v1/posts/" + postId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.summary.title").value("남을 글"))
            .andExpect(jsonPath("$.summary.authorNickname").value(WithdrawnUser.LABEL))

        mockMvc.perform(get("/api/v1/posts/" + postId + "/comments"))
            .andExpect(jsonPath("$.comments[0].body").value("남을 댓글"))
            .andExpect(jsonPath("$.comments[0].authorNickname").value(WithdrawnUser.LABEL))
    }

    @Test
    fun `어느 계정이었는지 화면에서 드러나지 않는다`() {
        withdraw()

        val body = mockMvc.perform(get("/api/v1/posts/" + postId)).andReturn().response.contentAsString
        assertTrue(!body.contains("떠나는이"), "옛 닉네임이 새면 안 됩니다")
        assertTrue(!body.contains("leaver@"), "이메일이 새면 안 됩니다")
    }

    @Test
    fun `식별 정보를 지운다`() {
        withdraw()

        val row = jdbcClient.sql("SELECT email, nickname, password_hash FROM users WHERE id = :id")
            .param("id", leaverId)
            .query { rs, _ -> Triple(rs.getString("email"), rs.getString("nickname"), rs.getString("password_hash")) }
            .single()

        // 개인정보를 남기지 않는 것이 탈퇴의 뜻이다.
        assertTrue(!row.first.contains("leaver@codekr.dev"))
        assertTrue(!row.second.contains("떠나는이"))
        assertEquals("", row.third)
    }

    @Test
    fun `닉네임을 다른 사람이 다시 쓸 수 있다`() {
        withdraw()

        // 나간 사람이 닉네임을 영구 점유하지 않는다.
        mockMvc.perform(
            post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupBody("new@codekr.dev", "password1234", "떠나는이")),
        ).andExpect(status().isCreated)
    }

    @Test
    fun `발급된 토큰이 만료 전이라도 통하지 않는다`() {
        withdraw()

        // JWT 는 상태가 없어서 그냥 두면 만료까지 계속 쓸 수 있다.
        mockMvc.perform(get("/api/v1/users/me/settings").header("Authorization", "Bearer " + leaverToken))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `탈퇴한 계정으로 로그인할 수 없다`() {
        userRepository.save(User("known@codekr.dev", encoded(), "알려진이", setOf(UserRole.USER)))
        val target = userRepository.findByEmail("known@codekr.dev")!!
        jdbcClient.sql("UPDATE users SET withdrawn_at = now() WHERE id = :id").param("id", target.id).update()

        // "탈퇴한 계정입니다" 라고 알리면 그 이메일이 쓰였다는 사실이 새어 나간다.
        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"known@codekr.dev\",\"password\":\"password1234\"}"),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `탈퇴한 사용자의 프로필은 열리지 않는다`() {
        withdraw()

        mockMvc.perform(get("/api/v1/users/떠나는이").header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `랭킹에서 빠진다`() {
        withdraw()

        // **컬럼이 아니라 랭킹 응답으로 확인한다** (#207). 전에는 ranking_opt_out 이 true 인지
        // 봤는데, 그 컬럼이 사라져도 "탈퇴자가 순위에 남지 않는다" 는 지켜져야 한다.
        // 없는 사람이 순위에 있으면 눌렀을 때 갈 곳이 없다.
        mockMvc.perform(get("/api/v1/rankings"))
            .andExpect(jsonPath("$.content[?(@.nickname == '떠나는이')]").isEmpty)
    }

    @Test
    fun `어드민의 강제 탈퇴도 같은 결과를 낸다`() {
        // 사유가 필수다 (#225) — 되돌릴 수 없는 조치라 기록의 사유가 유일한 답이 된다.
        mockMvc.perform(
            delete("/api/v1/admin/users/" + leaverId).param("reason", "약관 위반")
                .header("Authorization", "Bearer " + adminToken),
        )
            .andExpect(status().isNoContent)

        mockMvc.perform(get("/api/v1/posts/" + postId))
            .andExpect(jsonPath("$.summary.authorNickname").value(WithdrawnUser.LABEL))
        mockMvc.perform(get("/api/v1/users/me/settings").header("Authorization", "Bearer " + leaverToken))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `두 번 탈퇴할 수 없다`() {
        withdraw()

        mockMvc.perform(delete("/api/v1/admin/users/" + leaverId).header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNotFound)
    }

    private fun withdraw() {
        mockMvc.perform(delete("/api/v1/users/me").header("Authorization", "Bearer " + leaverToken))
            .andExpect(status().isNoContent)
    }

    private fun encoded(): String =
        org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("password1234").toString()
}
