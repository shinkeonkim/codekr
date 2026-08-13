package codekr.api.ranking

import codekr.api.queue.message.JudgeEventMessage
import codekr.api.ranking.entity.MIN_AFFILIATION_MEMBERS
import codekr.api.submission.entity.Verdict
import codekr.api.submission.service.JudgeResultRecorder
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
 * 소속끼리 겨루는 랭킹 (#400, #240 5단계).
 *
 * **값은 상위 N명의 점수 합이다.** 합이면 사람 많은 곳이 언제나 이기고, 평균이면
 * **잘하는 사람만 남기고 내보내는 유인**이 생긴다 — 뒤엣것이 훨씬 나쁘다.
 */
class AffiliationVsAffiliationIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var jdbcClient: JdbcClient
    @Autowired private lateinit var recorder: JudgeResultRecorder

    private var snu: Long = 0
    private var kaist: Long = 0
    private var seq = 0

    @BeforeEach
    fun setUp() {
        snu = affiliation("서울대학교")
        kaist = affiliation("카이스트")
        // 난이도 20 은 694점, 11 은 93점, 1 은 10점이다 (#85 가 못 박은 값).
        problem(1, level = 20)
        problem(2, level = 11)
        problem(3, level = 1)
    }

    @Test
    fun `상위 N명의 합으로 겨룬다 — 인원이 많다고 이기지 않는다`() {
        /*
          서울대는 다섯 명이 각각 93점(=465), 카이스트는 다섯 명이 694점을 풀고
          **여섯 번째 사람이 더 있다.** 여섯 번째가 아무리 풀어도 상위 5명 합은 그대로다.
        */
        repeat(MIN_AFFILIATION_MEMBERS) { member(snu, solves = 2) }
        repeat(MIN_AFFILIATION_MEMBERS) { member(kaist, solves = 1) }
        member(kaist, solves = 1) // 여섯 번째

        mockMvc.perform(get("/api/v1/rankings/affiliations"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content[0].name").value("카이스트"))
            .andExpect(jsonPath("$.content[0].rank").value(1))
            .andExpect(jsonPath("$.content[0].score").value(694 * MIN_AFFILIATION_MEMBERS))
            // 인원은 여섯이지만 점수는 다섯 명분이다. 그 사실이 화면에도 보여야 한다.
            .andExpect(jsonPath("$.content[0].memberCount").value(MIN_AFFILIATION_MEMBERS + 1))
            .andExpect(jsonPath("$.content[1].name").value("서울대학교"))
            .andExpect(jsonPath("$.content[1].score").value(93 * MIN_AFFILIATION_MEMBERS))
    }

    @Test
    fun `인원이 아니라 실력이 점수를 움직인다`() {
        // **끌어들일 이유도 내보낼 이유도 없다** — 그것이 상위 N명 합을 고른 이유다.
        repeat(MIN_AFFILIATION_MEMBERS) { member(snu, solves = 2) }
        val before = scoreOf("서울대학교")

        member(snu, solves = 1) // 694점짜리를 푸는 사람이 하나 더 들어온다
        val after = scoreOf("서울대학교")

        // 상위 5명 안에 들어가므로 오히려 점수는 오른다. 확인할 것은 **인원이 아니라
        // 실력이 점수를 움직인다**는 것이다.
        assert(after > before) { "잘하는 사람이 들어오면 오른다: $before -> $after" }

        member(snu, solves = 3) // 10점짜리만 푸는 사람 — 상위 5명 밖이다
        assert(scoreOf("서울대학교") == after) { "약한 사람이 더 들어와도 그대로다" }
    }

    @Test
    fun `최소 인원을 못 넘는 소속은 순위표에 없다`() {
        // **한 명짜리 학교가 1등이면 그 순위표는 아무 말도 하지 않는다** (기획서 6절).
        repeat(MIN_AFFILIATION_MEMBERS - 1) { member(snu, solves = 1) }

        mockMvc.perform(get("/api/v1/rankings/affiliations"))
            .andExpect(jsonPath("$.totalElements").value(0))
    }

    @Test
    fun `한 명이 더 붙으면 순위표에 들어온다`() {
        repeat(MIN_AFFILIATION_MEMBERS - 1) { member(snu, solves = 3) }
        member(snu, solves = 0) // 아직 아무것도 못 푼 사람이어도 인원은 인원이다

        mockMvc.perform(get("/api/v1/rankings/affiliations"))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].memberCount").value(MIN_AFFILIATION_MEMBERS))
            // 네 명이 10점씩. 못 푼 사람은 0점으로 합에 들어간다.
            .andExpect(jsonPath("$.content[0].score").value(10 * (MIN_AFFILIATION_MEMBERS - 1)))
    }

    @Test
    fun `탈퇴한 사람은 인원에서도 점수에서도 빠진다`() {
        // 없는 사람의 점수가 학교 점수에 남으면 그 숫자는 아무 말도 하지 않는다 (#207).
        repeat(MIN_AFFILIATION_MEMBERS) { member(snu, solves = 2) }
        val withdrawn = member(snu, solves = 1)
        jdbcClient.sql("UPDATE users SET withdrawn_at = now() WHERE id = :id").param("id", withdrawn).update()

        mockMvc.perform(get("/api/v1/rankings/affiliations"))
            .andExpect(jsonPath("$.content[0].memberCount").value(MIN_AFFILIATION_MEMBERS))
            .andExpect(jsonPath("$.content[0].score").value(93 * MIN_AFFILIATION_MEMBERS))
    }

    @Test
    fun `내려간 소속은 겨루지 않는다`() {
        repeat(MIN_AFFILIATION_MEMBERS) { member(snu, solves = 1) }
        jdbcClient.sql("UPDATE affiliations SET deleted_at = now() WHERE id = :id").param("id", snu).update()

        mockMvc.perform(get("/api/v1/rankings/affiliations"))
            .andExpect(jsonPath("$.totalElements").value(0))
    }

    @Test
    fun `이번 달 랭킹은 이번 달에 푼 것만 센다`() {
        repeat(MIN_AFFILIATION_MEMBERS) { member(snu, solves = 1) }

        mockMvc.perform(get("/api/v1/rankings/affiliations").param("period", "MONTHLY"))
            .andExpect(jsonPath("$.content[0].score").value(694 * MIN_AFFILIATION_MEMBERS))

        // 지난달에 푼 것으로 옮긴다. 이번 달 점수는 0이 되지만 **순위표에는 남는다** —
        // 인원은 그대로이므로 "이번 달에 아무도 안 푼 학교" 도 보여야 한다 (#391 의 규칙).
        jdbcClient.sql("UPDATE user_problem_scores SET solved_at = now() - interval '2 months'").update()

        mockMvc.perform(get("/api/v1/rankings/affiliations").param("period", "MONTHLY"))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].score").value(0))
    }

    @Test
    fun `누구나 볼 수 있다`() {
        // 사람 랭킹이 공개인 것과 같다 (#207). 로그인 없이 부른다.
        mockMvc.perform(get("/api/v1/rankings/affiliations"))
            .andExpect(status().isOk)
    }

    private fun affiliation(name: String): Long =
        jdbcClient.sql("INSERT INTO affiliations (name, kind) VALUES (:n, 'SCHOOL') RETURNING id")
            .param("n", name).query(Long::class.java).single()

    /** 그 소속 사람 하나. [solves] 가 0 이면 아직 아무것도 못 푼 사람이다. */
    private fun member(affiliationId: Long, solves: Long): Long {
        seq += 1
        val userId = userRepository.save(User("u$seq@codekr.dev", "x", "사람$seq", setOf(UserRole.USER))).id
        val emailId = jdbcClient.sql("INSERT INTO user_emails (user_id, email) VALUES (:u, :e) RETURNING id")
            .param("u", userId).param("e", "u$seq@x.ac.kr").query(Long::class.java).single()
        jdbcClient.sql(
            "INSERT INTO user_affiliations (user_id, affiliation_id, user_email_id) VALUES (:u, :a, :e)",
        ).param("u", userId).param("a", affiliationId).param("e", emailId).update()

        if (solves > 0) accept(userId, solves)
        return userId
    }

    /** 화면이 보는 값을 그대로 본다 — 질의를 시험에서 다시 쓰면 같은 실수를 두 번 한다. */
    private fun scoreOf(name: String): Int {
        val body = mockMvc.perform(get("/api/v1/rankings/affiliations")).andReturn()
            .response.getContentAsString(Charsets.UTF_8)
        return Regex("\"name\":\"$name\",\"kindLabel\":\"[^\"]*\",\"score\":(\\d+)")
            .find(body)?.groupValues?.get(1)?.toInt()
            ?: error("순위표에 '$name' 이 없습니다: $body")
    }

    private fun problem(id: Long, level: Int) {
        jdbcClient.sql(
            """
            INSERT INTO problems (id, slug, title, category, difficulty_level, description, published)
            VALUES (:id, 'p-' || :id, '문제 ' || :id, 'ALGORITHM', :level, '설명', true)
            """,
        ).param("id", id).param("level", level).update()
    }

    private fun accept(userId: Long, problemId: Long) {
        val submissionId = jdbcClient.sql(
            """
            INSERT INTO submissions (user_id, problem_id, runtime_id, source_code, status, kind)
            VALUES (:u, :p, 'python:3.12', 'print(3)', 'PENDING', 'USER') RETURNING id
            """,
        ).param("u", userId).param("p", problemId).query(Long::class.java).single()

        recorder.record(
            JudgeEventMessage(
                type = JudgeEventMessage.TYPE_COMPLETED,
                submissionId = submissionId,
                verdict = Verdict.ACCEPTED.name,
                passedCount = 1,
                totalCount = 1,
            ),
        )
    }
}
