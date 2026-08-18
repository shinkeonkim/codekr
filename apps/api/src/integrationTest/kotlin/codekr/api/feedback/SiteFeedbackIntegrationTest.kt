package codekr.api.feedback

import codekr.api.auth.security.JwtTokenProvider
import codekr.api.support.IntegrationTestBase
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import codekr.api.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 사이트 신고·제안 (#603).
 *
 * **전에는 받을 곳이 없었다** — 푸터가 GitHub 이슈 목록으로 나갔다. 계정이 있어야 하고
 * 공개된 곳에 써야 하니 사실상 안 받는 것에 가까웠다.
 */
class SiteFeedbackIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider

    private lateinit var adminToken: String
    private lateinit var userToken: String

    @BeforeEach
    fun setUp() {
        adminToken = tokenProvider.issueAccessToken(
            userRepository.save(User("admin@codekr.dev", "x", "관리자", setOf(UserRole.USER, UserRole.SUPERUSER))),
        )
        userToken = tokenProvider.issueAccessToken(
            userRepository.save(User("reporter@codekr.dev", "x", "제보자", setOf(UserRole.USER))),
        )
    }

    @Test
    fun `넣은 것이 어드민 목록에 쌓이고 내 목록에서도 보인다`() {
        submit(kind = "BUG", body = "제출 버튼이 안 눌립니다.", pageUrl = "https://코드.kr/problems/9")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.statusLabel").value("접수됨"))

        mockMvc.perform(get("/api/v1/admin/feedbacks").header("Authorization", "Bearer $adminToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].kindLabel").value("안 됩니다"))
            .andExpect(jsonPath("$.content[0].reporterNickname").value("제보자"))
            // 재현하려면 화면이 어디였는지가 있어야 한다.
            .andExpect(jsonPath("$.content[0].pageUrl").value("https://코드.kr/problems/9"))

        mockMvc.perform(get("/api/v1/feedbacks/me").header("Authorization", "Bearer $userToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
    }

    @Test
    fun `로그인하지 않으면 넣을 수 없다`() {
        mockMvc.perform(
            post("/api/v1/feedbacks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"kind":"BUG","body":"안 됩니다"}"""),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `빈 내용은 받지 않는다`() {
        submit(kind = "SUGGESTION", body = "   ").andExpect(status().isBadRequest)
    }

    /**
     * **이유 없는 거절은 "읽지 않았다" 와 구분되지 않는다** — #478 과 같은 규칙이다.
     */
    @Test
    fun `반영하지 않으려면 이유를 적어야 한다`() {
        val id = idOf(submit(kind = "SUGGESTION", body = "다크 모드를 주세요."))

        resolve(id, """{"status":"REJECTED"}""").andExpect(status().isBadRequest)
        resolve(id, """{"status":"REJECTED","resolution":"이미 있습니다."}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.statusLabel").value("반영하지 않음"))

        // 두 번 처리되면 알림이 두 번 가고 어느 것이 결론인지 알 수 없다.
        resolve(id, """{"status":"ACCEPTED"}""").andExpect(status().isBadRequest)
    }

    @Test
    fun `한 사람이 열어 둘 수 있는 수에 상한이 있다`() {
        repeat(5) { submit(kind = "OTHER", body = "의견 $it").andExpect(status().isCreated) }

        submit(kind = "OTHER", body = "여섯 번째").andExpect(status().isBadRequest)
    }

    @Test
    fun `어드민이 아니면 목록을 볼 수 없다`() {
        mockMvc.perform(get("/api/v1/admin/feedbacks").header("Authorization", "Bearer $userToken"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `로그인하지 못하는 사람도 알릴 수 있다`() {
        /*
          **가장 급한 신고가 로그인 바깥에 있다** (#611) — "가입이 안 됩니다",
          "인증 메일이 안 옵니다" 는 로그인해서 넣을 수 없다.
        */
        anonymous("가입이 안 됩니다. 인증 메일이 오지 않습니다.")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.anonymous").value(true))
            .andExpect(jsonPath("$.reporterId").doesNotExist())
    }

    @Test
    fun `어드민 목록에서 회원 것과 구별된다`() {
        // 같은 목록에 섞여 있으면 "이 사람에게 답을 줄 수 있나" 를 매번 다시 판단하게 된다.
        submit(kind = "BUG", body = "회원이 넣은 것").andExpect(status().isCreated)
        anonymous("비회원이 넣은 것").andExpect(status().isCreated)

        val body = mockMvc.perform(
            get("/api/v1/admin/feedbacks").header("Authorization", "Bearer $adminToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(2))
            .andReturn().response.getContentAsString(Charsets.UTF_8)

        assert(body.contains("(비회원)")) { "비회원 표시가 없다: $body" }
    }

    @Test
    fun `비회원 신고를 처리해도 터지지 않는다`() {
        // **알릴 곳이 없다** — 연락처를 받지 않기로 했다. 알림을 건너뛰고 처리만 한다.
        val id = idOf(anonymous("비밀번호 재설정이 안 됩니다."))

        mockMvc.perform(
            post("/api/v1/admin/feedbacks/$id/resolution")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"status":"ACCEPTED","resolution":"고쳤습니다."}"""),
        ).andExpect(status().isOk)
    }

    @Test
    fun `비회원 신고에도 빈 내용은 받지 않는다`() {
        anonymous("   ").andExpect(status().isBadRequest)
    }

    /** 로그인 없이 넣는다. 주소로 세므로 헤더가 없어도 된다 (#611). */
    private fun anonymous(body: String) = mockMvc.perform(
        post("/api/v1/feedbacks/anonymous")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"kind":"BUG","body":"$body"}"""),
    )

    private fun submit(kind: String, body: String, pageUrl: String? = null) = mockMvc.perform(
        post("/api/v1/feedbacks")
            .header("Authorization", "Bearer $userToken")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """{"kind":"$kind","body":"$body"""" +
                    (pageUrl?.let { ""","pageUrl":"$it"""" } ?: "") + "}",
            ),
    )

    private fun resolve(id: Long, payload: String) = mockMvc.perform(
        post("/api/v1/admin/feedbacks/$id/resolution")
            .header("Authorization", "Bearer $adminToken")
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload),
    )

    private fun idOf(result: org.springframework.test.web.servlet.ResultActions): Long =
        Regex("\"id\":(\\d+)").find(result.andReturn().response.contentAsString)!!.groupValues[1].toLong()
}
