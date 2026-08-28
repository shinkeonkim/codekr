package codekr.api.problem

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
 * 정규식 문제 (#653).
 *
 * **이 유형에서 가장 위험한 것은 아무것도 묻지 않는 문제다.** 맞으면 안 되는 문자열이
 * 없으면 `.*` 가 통과하는데, 그것은 오류를 내지 않아 출제자가 알 수 없다 —
 * #605 에서 Redis 문제 하나가 틀린 답도 통과했던 것과 같은 종류다. 그래서 등록에서 막는다.
 */
class RegexProblemIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider

    private lateinit var adminToken: String

    @BeforeEach
    fun setUp() {
        adminToken = tokenProvider.issueAccessToken(
            userRepository.save(User("admin@codekr.dev", "x", "운영자", setOf(UserRole.ADMIN))),
        )
    }

    private fun create(cases: String, slug: String = "email-pattern", extra: String = "") =
        mockMvc.perform(
            post("/api/v1/admin/problems")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"slug":"$slug","title":"이메일 꼴 찾기","category":"ALGORITHM","problemKind":"JUDGE_REGEX",
                     "description":"이메일처럼 보이는 것만 맞는 패턴을 쓰시오.","published":true,
                     "regexSpec":{"cases":"$cases"$extra}}
                    """.trimIndent(),
                ),
        )

    @Test
    fun `확인 문자열이 갖춰지면 등록된다`() {
        create("+a@b.com\\n-a@b\\n").andExpect(status().isCreated)
    }

    /**
     * **맞으면 안 되는 문자열이 없으면 문제가 아니다.**
     *
     * `.*` 가 통과하고, 그 사실을 알려 주는 것이 아무것도 없다.
     */
    @Test
    fun `맞으면 안 되는 문자열이 없으면 거부한다`() {
        create("+a@b.com\\n+c@d.org\\n")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(".*")))
    }

    @Test
    fun `맞아야 하는 문자열이 없으면 거부한다`() {
        create("-a@b\\n-c\\n").andExpect(status().isBadRequest)
    }

    /** 판정 표시가 없는 줄은 **무엇을 뜻하는지 알 수 없다.** 짐작하지 않고 막는다. */
    @Test
    fun `판정 표시가 없는 줄이 있으면 거부한다`() {
        create("+a@b.com\\na@b\\n").andExpect(status().isBadRequest)
    }

    @Test
    fun `정규식 문제가 아닌데 스펙을 실으면 거부한다`() {
        mockMvc.perform(
            post("/api/v1/admin/problems")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"slug":"mixed","title":"섞인 문제","category":"ALGORITHM","problemKind":"JUDGE_STDIO",
                     "description":"설명","published":false,
                     "regexSpec":{"cases":"+a\\n-b\\n"}}
                    """.trimIndent(),
                ),
        ).andExpect(status().isBadRequest)
    }

    /**
     * **패턴을 푸는 사람에게는 확인 문자열이 보이지 않는다.**
     *
     * 보이면 그것만 통과하는 패턴을 쓰면 되므로 문제가 무너진다 — 히든 테스트케이스를
     * 상세에 담지 않는 것과 같은 이유다.
     */
    @Test
    fun `공개 상세에는 확인 문자열이 실리지 않는다`() {
        create("+a@b.com\\n-a@b\\n").andExpect(status().isCreated)

        val body = mockMvc.perform(get("/api/v1/problems/email-pattern"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.problemKind").value("JUDGE_REGEX"))
            // 이 유형으로 풀 수 있는 실행 환경만 내린다 (#60).
            .andExpect(jsonPath("$.runtimes[0].id").value("regex:python"))
            .andReturn().response.contentAsString

        assert(!body.contains("a@b.com")) { "확인 문자열이 응답에 남아 있습니다" }
    }

    /** 어드민은 고쳐야 하므로 받는다 — 푸는 사람과 반대다. */
    @Test
    fun `어드민 상세에는 확인 문자열이 온다`() {
        create("+a@b.com\\n-a@b\\n", extra = ""","fullMatch":true,"ignoreCase":true""")
            .andExpect(status().isCreated)
        val id = jdbcOfBase.sql("SELECT id FROM problems ORDER BY id DESC LIMIT 1")
            .query(Long::class.java).single()

        mockMvc.perform(
            get("/api/v1/admin/problems/$id").header("Authorization", "Bearer $adminToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.regexSpec.cases").value("+a@b.com\n-a@b"))
            .andExpect(jsonPath("$.regexSpec.fullMatch").value(true))
            .andExpect(jsonPath("$.regexSpec.ignoreCase").value(true))
    }
}
