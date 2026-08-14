package codekr.api.admin

import codekr.api.auth.security.JwtTokenProvider
import codekr.api.support.IntegrationTestBase
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import codekr.api.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 어드민 통계 대시보드 (#550).
 *
 * **목록만 있고 추세가 없었다.** 여기서 확인하는 것은 숫자가 맞는가와, 아무도 못 보는
 * 값이 아무에게나 새지 않는가다.
 */
class AdminStatsIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var jdbcClient: JdbcClient

    private lateinit var adminToken: String
    private lateinit var memberToken: String

    @BeforeEach
    fun setUp() {
        adminToken = tokenProvider.issueAccessToken(
            userRepository.save(User("admin@codekr.dev", "x", "관리자", setOf(UserRole.USER, UserRole.ADMIN))),
        )
        val member = userRepository.save(User("member@codekr.dev", "x", "회원", setOf(UserRole.USER)))
        memberToken = tokenProvider.issueAccessToken(member)

        val problemId = jdbcClient.sql(
            """
            INSERT INTO problems (slug, title, category, description, published,
                                  difficulty_level, difficulty_state)
            VALUES ('stats-p', '통계용', 'ALGORITHM', '설명', true, 5, 'RATED') RETURNING id
            """,
        ).query(Long::class.java).single()

        // 오늘 셋: 정답 하나, 오답 하나, 시스템 오류 하나.
        listOf("ACCEPTED", "WRONG_ANSWER", "SYSTEM_ERROR").forEach { verdict ->
            jdbcClient.sql(
                """
                INSERT INTO submissions (user_id, problem_id, runtime_id, source_code, status, verdict, kind)
                VALUES (:u, :p, 'python:3.12', 'print(1)', 'COMPLETED', :v, 'USER')
                """,
            ).param("u", member.id).param("p", problemId).param("v", verdict).update()
        }
    }

    @Test
    fun `제출 추세가 날짜별로 온다`() {
        mockMvc.perform(stats())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.days").value(30))
            .andExpect(jsonPath("$.submissions.length()").value(30))
            .andExpect(jsonPath("$.submissions[29].total").value(3))
            .andExpect(jsonPath("$.submissions[29].accepted").value(1))
    }

    @Test
    fun `제출이 없는 날도 0 으로 이어진다`() {
        /*
          **없는 것과 적은 것은 다른 말이다.** 행이 없는 날을 건너뛰고 그리면 아무도
          안 낸 날이 완만한 하락처럼 보인다.
        */
        mockMvc.perform(stats())
            .andExpect(jsonPath("$.submissions[0].total").value(0))
            .andExpect(jsonPath("$.submissions[0].day").exists())
    }

    @Test
    fun `판정 분포에 SYSTEM_ERROR 가 보인다`() {
        // 이 값을 보려고 있는 그림이다 — 늘면 우리 잘못이고, 사용자는 자기 코드를 의심한다.
        val body = mockMvc.perform(stats()).andReturn().response.getContentAsString(Charsets.UTF_8)

        assert(body.contains("SYSTEM_ERROR")) { "판정 분포에 SYSTEM_ERROR 가 없습니다: $body" }
        assert(body.contains("python:3.12")) { "언어 분포가 없습니다: $body" }
    }

    @Test
    fun `가입 추세와 유형별 문제 수가 함께 온다`() {
        // **한 번에 준다.** 화면이 그림마다 부르면 어드민 화면 한 장이 다섯 번 요청한다.
        mockMvc.perform(stats())
            .andExpect(jsonPath("$.signups.length()").value(30))
            .andExpect(jsonPath("$.signups[29].total").value(2))
            .andExpect(jsonPath("$.problemKinds[0].name").value("JUDGE_STDIO"))
            .andExpect(jsonPath("$.problemKinds[0].total").value(1))
    }

    @Test
    fun `기간은 7일에서 90일 사이만 받는다`() {
        // 이 질의가 감당 가능한 이유는 기간으로 먼저 자르기 때문이다 (#105).
        mockMvc.perform(stats().param("days", "365")).andExpect(status().isBadRequest)
        mockMvc.perform(stats().param("days", "1")).andExpect(status().isBadRequest)
        mockMvc.perform(stats().param("days", "7")).andExpect(status().isOk)
    }

    @Test
    fun `어드민이 아니면 볼 수 없다`() {
        // 가입 추세·판정 분포는 우리가 얼마나 아픈지를 그대로 드러낸다.
        mockMvc.perform(
            get("/api/v1/admin/stats").header("Authorization", "Bearer $memberToken"),
        ).andExpect(status().isForbidden)

        mockMvc.perform(get("/api/v1/admin/stats")).andExpect(status().isUnauthorized)
    }

    private fun stats() = get("/api/v1/admin/stats").header("Authorization", "Bearer $adminToken")
}
