package codekr.api.board

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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.temporal.ChronoUnit

/** 문제별 질문 (#139). */
class ProblemQuestionIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var jdbcClient: JdbcClient

    private var userId: Long = 0
    private lateinit var token: String

    @BeforeEach
    fun setUp() {
        val user = userRepository.save(User("u@codekr.dev", "x", "질문자", setOf(UserRole.USER)))
        userId = user.id
        token = tokenProvider.issueAccessToken(user)

        jdbcClient.sql(
            """
            INSERT INTO problems (id, slug, title, category, difficulty_level, description, published)
            VALUES (1, 'two-sum', '두 수의 합', 'ALGORITHM', 1, '설명', true),
                   (2, 'other', '다른 문제', 'ALGORITHM', 1, '설명', true)
            """,
        ).update()
    }

    @Test
    fun `문제에 붙은 질문만 그 문제의 탭에 보인다`() {
        ask(problemId = 1, title = "여기서 막힙니다")
        ask(problemId = 2, title = "다른 문제 질문")
        community("자유 글")

        mockMvc.perform(get("/api/v1/posts/by-problem/1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].title").value("여기서 막힙니다"))
            .andExpect(jsonPath("$.content[0].problemSlug").value("two-sum"))
    }

    @Test
    fun `커뮤니티 목록에도 함께 보인다`() {
        ask(problemId = 1, title = "문제 질문")
        community("자유 글")

        // 분리하면 질문이 두 곳에 흩어지고, "다음 사람이 먼저 읽고 간다" 는 목적이 약해진다.
        mockMvc.perform(get("/api/v1/posts"))
            .andExpect(jsonPath("$.content.length()").value(2))
            // 어느 문제의 질문인지 보여야 한다.
            .andExpect(jsonPath("$.content[?(@.title == '문제 질문')].problemTitle").value("두 수의 합"))
    }

    @Test
    fun `문제 질문은 코드를 기본으로 가린다`() {
        // 아직 못 푼 사람에게 답이 보이면 그 문제의 값이 떨어진다.
        // "푼 사람에게만" 으로 막으면 질문하려면 먼저 풀어야 하는 모순이 생긴다.
        val id = ask(problemId = 1, title = "질문")

        mockMvc.perform(get("/api/v1/posts/" + id))
            .andExpect(jsonPath("$.hideCode").value(true))
    }

    @Test
    fun `커뮤니티 글은 가리지 않는다`() {
        val id = community("자유 글")

        mockMvc.perform(get("/api/v1/posts/" + id))
            .andExpect(jsonPath("$.hideCode").value(false))
    }

    @Test
    fun `대회가 진행 중인 문제에는 질문할 수 없다`() {
        // 질문이 곧 힌트가 되고, 참가자마다 그것을 본 사람과 못 본 사람이 갈린다.
        startContestOn(problemId = 1)

        mockMvc.perform(
            post("/api/v1/posts")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload("QUESTION", "대회 중 질문", 1)),
        ).andExpect(status().isBadRequest)

        // 대회에 없는 문제는 그대로 된다.
        mockMvc.perform(
            post("/api/v1/posts")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload("QUESTION", "다른 문제 질문", 2)),
        ).andExpect(status().isCreated)
    }

    @Test
    fun `없는 문제에는 질문할 수 없다`() {
        mockMvc.perform(
            post("/api/v1/posts")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload("QUESTION", "유령 문제", 999)),
        ).andExpect(status().isNotFound)
    }

    private fun startContestOn(problemId: Long) {
        val startsAt = Instant.now().minus(10, ChronoUnit.MINUTES)
        jdbcClient.sql(
            """
            INSERT INTO contests (id, slug, title, starts_at, ends_at, freeze_minutes, status)
            VALUES (1, 'cup', '컵', :startsAt::timestamptz, :endsAt::timestamptz, 0, 'PUBLISHED')
            """,
        )
            .param("startsAt", startsAt.toString())
            .param("endsAt", startsAt.plus(2, ChronoUnit.HOURS).toString())
            .update()
        jdbcClient.sql(
            "INSERT INTO contest_problems (contest_id, problem_id, seq, score) VALUES (1, :problemId, 1, 100)",
        ).param("problemId", problemId).update()
    }

    private fun ask(problemId: Long, title: String): Long = create(payload("QUESTION", title, problemId))

    private fun community(title: String): Long = create(payload("FREE", title, null))

    private fun create(payload: String): Long {
        val response = mockMvc.perform(
            post("/api/v1/posts")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload),
        ).andExpect(status().isCreated).andReturn().response.contentAsString

        return Regex("\"id\":(\\d+)").find(response)!!.groupValues[1].toLong()
    }

    private fun payload(board: String, title: String, problemId: Long?): String {
        val problem = if (problemId == null) "" else ",\"problemId\":" + problemId
        return "{\"board\":\"" + board + "\",\"title\":\"" + title + "\",\"body\":\"본문\"" + problem + "}"
    }
}
