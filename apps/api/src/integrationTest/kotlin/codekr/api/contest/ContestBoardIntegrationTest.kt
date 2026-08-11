package codekr.api.contest

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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.temporal.ChronoUnit

/** 대회 공지와 질의 (#147). */
class ContestBoardIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var jdbcClient: JdbcClient

    private var askerId: Long = 0
    private var otherId: Long = 0
    private lateinit var askerToken: String
    private lateinit var otherToken: String
    private lateinit var managerToken: String

    @BeforeEach
    fun setUp() {
        val asker = userRepository.save(User("a@codekr.dev", "x", "참가자", setOf(UserRole.USER)))
        askerId = asker.id
        askerToken = tokenProvider.issueAccessToken(asker)
        val other = userRepository.save(User("b@codekr.dev", "x", "다른참가자", setOf(UserRole.USER)))
        otherId = other.id
        otherToken = tokenProvider.issueAccessToken(other)
        managerToken = tokenProvider.issueAccessToken(
            userRepository.save(User("m@codekr.dev", "x", "운영자", setOf(UserRole.USER, UserRole.CONTEST_MANAGER))),
        )

        val startsAt = Instant.now().minus(10, ChronoUnit.MINUTES)
        jdbcClient.sql(
            """
            INSERT INTO contests (id, slug, title, starts_at, ends_at, freeze_minutes, status)
            VALUES (1, 'cup', '컵', :s::timestamptz, :e::timestamptz, 0, 'PUBLISHED')
            """,
        ).param("s", startsAt.toString()).param("e", startsAt.plus(2, ChronoUnit.HOURS).toString()).update()
        jdbcClient.sql(
            "INSERT INTO contest_registrations (contest_id, user_id) VALUES (1, :a), (1, :b)",
        ).param("a", askerId).param("b", otherId).update()
    }

    @Test
    fun `공지는 참가자 전원에게 알림으로 간다`() {
        // 대회 중의 공지는 읽지 않으면 손해를 보는 정보다.
        addNotice("문제 B 에 오류가 있었습니다")

        mockMvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + askerToken))
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].title").value("[컵] 문제 B 에 오류가 있었습니다"))
        mockMvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + otherToken))
            .andExpect(jsonPath("$.content.length()").value(1))
    }

    @Test
    fun `공지는 참가하지 않은 사람도 읽는다`() {
        addNotice("공지")

        // 대회가 끝난 뒤 기록으로 남아야 한다.
        mockMvc.perform(get("/api/v1/contests/cup/notices"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
    }

    @Test
    fun `운영자만 공지를 올린다`() {
        mockMvc.perform(
            post("/api/v1/contests/cup/notices")
                .header("Authorization", "Bearer " + askerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"가짜 공지\",\"body\":\"내용\"}"),
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `참가하지 않으면 질문할 수 없다`() {
        val outsider = tokenProvider.issueAccessToken(
            userRepository.save(User("out@codekr.dev", "x", "구경꾼", setOf(UserRole.USER))),
        )

        mockMvc.perform(
            post("/api/v1/contests/cup/questions")
                .header("Authorization", "Bearer " + outsider)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"질문\"}"),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `비공개 답변은 질문한 사람만 본다`() {
        val id = ask("이 문제의 입력 범위가 맞나요")
        answer(id, "제 사정에만 해당하는 답", public = false)

        // 남의 비공개 답변이 보이면 비공개의 뜻이 없다.
        mockMvc.perform(get("/api/v1/contests/cup/questions").header("Authorization", "Bearer " + askerToken))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].answer").value("제 사정에만 해당하는 답"))
            .andExpect(jsonPath("$[0].mine").value(true))

        mockMvc.perform(get("/api/v1/contests/cup/questions").header("Authorization", "Bearer " + otherToken))
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `공개 답변은 전원이 보고 전원에게 알림이 간다`() {
        val id = ask("입력에 공백이 있나요")
        answer(id, "예, 있습니다", public = true)

        // 한 사람에게만 준 정보가 유리하게 작용하면 안 되는 질문이라서 공개하는 것이다.
        mockMvc.perform(get("/api/v1/contests/cup/questions").header("Authorization", "Bearer " + otherToken))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].answerPublic").value(true))
            .andExpect(jsonPath("$[0].mine").value(false))

        mockMvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + otherToken))
            .andExpect(jsonPath("$.content[0].title").value("[컵] 질의에 답변이 올라왔습니다"))
    }

    @Test
    fun `답하지 않은 질문은 남에게 보이지 않는다`() {
        ask("아직 답이 없는 질문")

        // 질문 자체가 힌트가 될 수 있다.
        mockMvc.perform(get("/api/v1/contests/cup/questions").header("Authorization", "Bearer " + otherToken))
            .andExpect(jsonPath("$.length()").value(0))
        // 운영자는 전부 본다.
        mockMvc.perform(get("/api/v1/contests/cup/questions").header("Authorization", "Bearer " + managerToken))
            .andExpect(jsonPath("$.length()").value(1))
    }

    @Test
    fun `운영자만 답한다`() {
        val id = ask("질문")

        mockMvc.perform(
            put("/api/v1/contests/cup/questions/" + id + "/answer")
                .header("Authorization", "Bearer " + otherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answer\":\"제멋대로 답\",\"public\":true}"),
        ).andExpect(status().isForbidden)
    }

    private fun addNotice(title: String) {
        mockMvc.perform(
            post("/api/v1/contests/cup/notices")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + title + "\",\"body\":\"내용입니다\"}"),
        ).andExpect(status().isOk)
    }

    private fun ask(body: String): Long {
        mockMvc.perform(
            post("/api/v1/contests/cup/questions")
                .header("Authorization", "Bearer " + askerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"" + body + "\"}"),
        ).andExpect(status().isOk)

        return jdbcClient.sql("SELECT max(id) FROM contest_questions").query(Long::class.java).single()
    }

    private fun answer(questionId: Long, text: String, public: Boolean) {
        mockMvc.perform(
            put("/api/v1/contests/cup/questions/" + questionId + "/answer")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answer\":\"" + text + "\",\"public\":" + public + "}"),
        ).andExpect(status().isNoContent)
    }
}
