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
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 모범 답안 (#719).
 *
 * **자격이 없으면 없는 것처럼 답한다.** "있지만 못 본다" 를 알려 주면 어떤 문제에
 * 모범 답안이 있는지가 보이고, 대회 중에는 그것만으로도 정보다.
 */
class EditorialIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var jdbcClient: JdbcClient

    private var solverId: Long = 0
    private var solverToken: String = ""
    private var strangerToken: String = ""
    private var adminToken: String = ""

    @BeforeEach
    fun setUp() {
        val solver = userRepository.save(User("solver@codekr.dev", "x", "푼사람", setOf(UserRole.USER)))
        solverId = solver.id
        solverToken = tokenProvider.issueAccessToken(solver)
        strangerToken = tokenProvider.issueAccessToken(
            userRepository.save(User("stranger@codekr.dev", "x", "안푼사람", setOf(UserRole.USER))),
        )
        adminToken = tokenProvider.issueAccessToken(
            userRepository.save(
                User("setter@codekr.dev", "x", "출제자", setOf(UserRole.USER, UserRole.PROBLEM_SETTER)),
            ),
        )
    }

    @Test
    fun `푼 사람은 모범 답안을 본다`() {
        problem(1, "solved-one")
        writeEditorial(1)
        markSolved(1)

        mockMvc.perform(get("/api/v1/problems/solved-one/editorial").header("Authorization", "Bearer $solverToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.body").value("이렇게 풉니다."))
            .andExpect(jsonPath("$.referenceAnswer").value("git add app.py"))
            .andExpect(jsonPath("$.referenceLabel").value("git 명령"))
    }

    @Test
    fun `안 푼 사람에게는 없는 것처럼 답한다`() {
        problem(2, "unsolved-one")
        writeEditorial(2)

        // **403 이 아니라 404 다.** "있지만 못 본다" 를 알려 주면 그 자체가 신호가 된다.
        mockMvc.perform(get("/api/v1/problems/unsolved-one/editorial").header("Authorization", "Bearer $strangerToken"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `모범 답안이 없는 문제도 같은 답을 준다`() {
        // 없는 것과 못 보는 것이 구별되면, 그 차이를 훑어서 목록을 만들 수 있다.
        problem(3, "no-editorial")
        markSolved(3)

        mockMvc.perform(get("/api/v1/problems/no-editorial/editorial").header("Authorization", "Bearer $solverToken"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `대회가 도는 동안에는 이미 푼 사람도 못 본다`() {
        /*
          **대회 전에 그 문제를 풀어 둔 사람**이 있다. 그 사람이 대회 중에 모범 답안을
          열 수 있으면, 같은 문제를 처음 보는 참가자와 조건이 달라진다.
        */
        problem(4, "contest-one")
        writeEditorial(4)
        markSolved(4)
        contest(1, "running", 4, startsAt = Instant.now().minus(1, ChronoUnit.HOURS), endsAt = Instant.now().plusSeconds(3600))

        mockMvc.perform(get("/api/v1/problems/contest-one/editorial").header("Authorization", "Bearer $solverToken"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `대회가 끝나면 다시 보인다`() {
        problem(5, "contest-done")
        writeEditorial(5)
        markSolved(5)
        contest(2, "ended", 5, startsAt = Instant.now().minus(3, ChronoUnit.HOURS), endsAt = Instant.now().minus(1, ChronoUnit.HOURS))

        mockMvc.perform(get("/api/v1/problems/contest-done/editorial").header("Authorization", "Bearer $solverToken"))
            .andExpect(status().isOk)
    }

    @Test
    fun `대회에서 제외된 문제는 막지 않는다`() {
        // 제외(#86)는 그 문제가 대회에서 빠졌다는 뜻이다 — 막을 이유가 사라진다.
        problem(6, "contest-excluded")
        writeEditorial(6)
        markSolved(6)
        contest(3, "excl", 6, startsAt = Instant.now().minus(1, ChronoUnit.HOURS), endsAt = Instant.now().plusSeconds(3600))
        jdbcClient.sql("UPDATE contest_problems SET excluded_at = now() WHERE problem_id = 6").update()

        mockMvc.perform(get("/api/v1/problems/contest-excluded/editorial").header("Authorization", "Bearer $solverToken"))
            .andExpect(status().isOk)
    }

    @Test
    fun `문제 상세에는 모범 답안이 담기지 않는다`() {
        /*
          **담을 자리를 안 만드는 것이 유일하고 확실한 방어다** — 히든 테스트케이스와
          정답 코드를 공개 DTO 에 넣지 않는 것과 같은 규칙이다. 조건부 필드로 넣으면
          언젠가 그 조건이 어긋나고, 어긋난 것을 아무도 모른다.
        */
        problem(7, "detail-clean")
        writeEditorial(7)
        markSolved(7)

        val body = mockMvc.perform(get("/api/v1/problems/detail-clean").header("Authorization", "Bearer $solverToken"))
            .andExpect(status().isOk)
            .andReturn().response.getContentAsString(Charsets.UTF_8)

        assert(!body.contains("이렇게 풉니다")) { "상세에 모범 답안이 실렸다: $body" }
        assert(!body.contains("editorial")) { "상세에 모범 답안 자리가 생겼다: $body" }
    }

    @Test
    fun `로그인하지 않으면 열리지 않는다`() {
        // 공개로 두면 자격 없다는 응답을 누구나 받고, 그 응답들의 차이가 곧 목록이 된다.
        problem(8, "anon")
        writeEditorial(8)

        mockMvc.perform(get("/api/v1/problems/anon/editorial")).andExpect(status().isUnauthorized)
    }

    private fun problem(id: Long, slug: String) {
        jdbcClient.sql(
            """
            INSERT INTO problems (id, slug, title, category, difficulty_level, description, published)
            VALUES (:id, :slug, '문제', 'ALGORITHM', 5, '설명', true)
            """,
        ).param("id", id).param("slug", slug).update()
    }

    /** 어드민 경로로 쓴다 — 저장까지 함께 확인한다. */
    private fun writeEditorial(problemId: Long) {
        mockMvc.perform(
            put("/api/v1/admin/problems/$problemId/editorial")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"body":"이렇게 풉니다.","referenceAnswer":"git add app.py","referenceLabel":"git 명령"}""",
                ),
        ).andExpect(status().isOk)
    }

    /** 랭킹 점수 표가 곧 "푼 기록" 이다 (#57). */
    private fun markSolved(problemId: Long) {
        jdbcClient.sql(
            """
            INSERT INTO user_problem_scores (user_id, problem_id, score, solved_at)
            VALUES (:u, :p, 10, now())
            """,
        ).param("u", solverId).param("p", problemId).update()
    }

    private fun contest(id: Long, slug: String, problemId: Long, startsAt: Instant, endsAt: Instant) {
        jdbcClient.sql(
            """
            INSERT INTO contests (id, slug, title, starts_at, ends_at, status)
            VALUES (:id, :slug, '대회', :s, :e, 'PUBLISHED')
            """,
        ).param("id", id).param("slug", slug)
            // 드라이버가 Instant 의 SQL 타입을 못 고른다 — 시각대를 붙여 넘긴다.
            .param("s", java.sql.Timestamp.from(startsAt))
            .param("e", java.sql.Timestamp.from(endsAt))
            .update()
        jdbcClient.sql(
            "INSERT INTO contest_problems (contest_id, problem_id, seq, score) VALUES (:c, :p, 1, 100)",
        ).param("c", id).param("p", problemId).update()
    }
}
