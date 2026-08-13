package codekr.api.contest

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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 목록에 없는 대회 (#465).
 *
 * 전에는 **목록에 띄우거나 아무도 못 들어오거나 둘뿐**이었다. 그 사이가 없어서
 * 스터디·사내 대회를 낼 수 없었다.
 *
 * **`UNLISTED` 는 비밀이 아니다.** 목록에 없을 뿐 주소로는 열린다 — 대회에서 정말
 * 새면 안 되는 것(문제·순위표)은 시간과 참가 여부가 막는다(#61·#86).
 */
class UnlistedContestIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var jdbcClient: JdbcClient

    private var token: String = ""
    private var userId: Long = 0

    @BeforeEach
    fun setUp() {
        val user = userRepository.save(User("solver@codekr.dev", "x", "참가자", setOf(UserRole.USER)))
        userId = user.id
        token = tokenProvider.issueAccessToken(user)
    }

    @Test
    fun `목록에는 공개 대회만 나온다`() {
        contest("public-one", "PUBLIC")
        contest("hidden-one", "UNLISTED")

        mockMvc.perform(get("/api/v1/contests"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].slug").value("public-one"))
    }

    @Test
    fun `주소를 알면 들어간다`() {
        // 그것이 "링크가 있는 사람만" 의 뜻이다. 막는 것이었으면 DRAFT 로 두면 된다.
        contest("hidden-one", "UNLISTED")

        mockMvc.perform(get("/api/v1/contests/hidden-one"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.summary.visibility").value("UNLISTED"))
            .andExpect(jsonPath("$.summary.visibilityLabel").value("링크가 있는 사람만"))
    }

    @Test
    fun `준비 중인 대회는 여전히 없는 것과 같다`() {
        /*
          **`status` 와 범위는 다른 값이다** (#465). 범위가 생겼다고 `DRAFT` 가 열리면
          "준비 중" 이라는 뜻이 사라진다.
        */
        contest("draft-one", "UNLISTED", status = "DRAFT")

        mockMvc.perform(get("/api/v1/contests/draft-one")).andExpect(status().isNotFound)
    }

    @Test
    fun `등록하면 내 대회 목록에서 다시 찾을 수 있다`() {
        /*
          **링크를 잃으면 끝인 상태를 만들지 않는다.** 목록에 없는 대회에 들어갔는데
          어디서도 안 보이면 주소를 잃는 순간 참가한 대회가 사라진다.
        */
        contest("hidden-one", "UNLISTED")

        mockMvc.perform(
            post("/api/v1/contests/hidden-one/registrations").header("Authorization", "Bearer $token"),
        ).andExpect(status().isNoContent)

        mockMvc.perform(get("/api/v1/contests/registered").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].slug").value("hidden-one"))

        // 그래도 공개 목록에는 여전히 없다.
        mockMvc.perform(get("/api/v1/contests"))
            .andExpect(jsonPath("$.totalElements").value(0))
    }

    @Test
    fun `등록하지 않은 대회는 내 목록에 없다`() {
        contest("public-one", "PUBLIC")

        mockMvc.perform(get("/api/v1/contests/registered").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.totalElements").value(0))
    }

    @Test
    fun `기존 대회는 전부 공개다`() {
        // 지금 열려 있는 것을 조용히 숨기면 참가자가 잃어버린다 — 기본값이 그것을 막는다.
        jdbcClient.sql(
            """
            INSERT INTO contests (slug, title, description, starts_at, ends_at, status, created_by)
            VALUES ('legacy', '옛 대회', '', now() - interval '2 hour', now() + interval '2 hour',
                    'PUBLISHED', :u)
            """,
        ).param("u", userId).update()

        mockMvc.perform(get("/api/v1/contests"))
            .andExpect(jsonPath("$.content[0].visibility").value("PUBLIC"))
    }

    private fun contest(slug: String, visibility: String, status: String = "PUBLISHED") {
        jdbcClient.sql(
            """
            INSERT INTO contests (slug, title, description, starts_at, ends_at, status, visibility, created_by)
            VALUES (:slug, :slug, '', now() - interval '1 hour', now() + interval '1 hour',
                    :status, :visibility, :u)
            """,
        )
            .param("slug", slug)
            .param("status", status)
            .param("visibility", visibility)
            .param("u", userId)
            .update()
    }
}
