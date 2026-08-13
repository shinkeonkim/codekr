package codekr.api.ranking

import codekr.api.queue.message.JudgeEventMessage
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
 * 소속 안 랭킹 (#399, #240 4단계).
 *
 * **전체에서 300등인 것은 대부분의 사람에게 아무 뜻이 없다.** 같은 학교 사람들 사이의
 * 순위는 다르다 — 닿을 수 있는 거리에 있는 비교라서 다음 한 문제를 부른다.
 */
class AffiliationRankingIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var jdbcClient: JdbcClient
    @Autowired private lateinit var recorder: JudgeResultRecorder

    private var snu: Long = 0
    private var kakao: Long = 0
    private var alice: Long = 0
    private var bob: Long = 0
    private var carol: Long = 0

    @BeforeEach
    fun setUp() {
        snu = affiliation("서울대학교")
        kakao = affiliation("카카오")

        alice = user("alice@codekr.dev", "앨리스")
        bob = user("bob@codekr.dev", "밥")
        carol = user("carol@codekr.dev", "캐럴")

        problem(1, level = 20)
        problem(2, level = 11)
        problem(3, level = 1)
    }

    @Test
    fun `소속을 주면 그 사람들만 나오고 등수를 다시 매긴다`() {
        /*
          **모집단을 좁히는 것이지 정렬을 바꾸는 것이 아니다.** 그래서 등수는 그 안에서
          1위부터 다시 매겨진다 — "우리 학교에서 3등" 이 이 기능의 이유다.
        */
        accept(alice, 1) // 가장 높은 점수
        accept(bob, 2)
        accept(carol, 3)
        attach(bob, snu)
        attach(carol, snu)

        mockMvc.perform(get("/api/v1/rankings").param("affiliationId", snu.toString()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(2))
            // 전체에서는 2·3등이지만 여기서는 1·2등이다.
            .andExpect(jsonPath("$.content[0].nickname").value("밥"))
            .andExpect(jsonPath("$.content[0].rank").value(1))
            .andExpect(jsonPath("$.content[1].nickname").value("캐럴"))
            .andExpect(jsonPath("$.content[1].rank").value(2))
    }

    @Test
    fun `소속이 없으면 전체 순위표다`() {
        accept(alice, 1)
        attach(bob, snu)

        mockMvc.perform(get("/api/v1/rankings"))
            .andExpect(jsonPath("$.totalElements").value(3))
    }

    @Test
    fun `한 사람이 여러 소속 랭킹에 동시에 나온다`() {
        /*
          **그것이 의도다** (기획서 4절). 서울대 랭킹에서도 보이고 카카오 랭킹에서도
          보인다 — 각 랭킹은 "지금 그곳의 메일을 확인한 사람들 사이의 순위" 라는 뜻을
          그대로 가진다. 두 순위가 서로 비교 가능할 필요는 없다.
        */
        accept(alice, 1)
        attach(alice, snu)
        attach(alice, kakao)

        listOf(snu, kakao).forEach { id ->
            mockMvc.perform(get("/api/v1/rankings").param("affiliationId", id.toString()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].nickname").value("앨리스"))
        }
    }

    @Test
    fun `아직 못 푼 사람도 소속 랭킹에 나온다`() {
        // #391 이 전체 순위표에서 정한 것이 여기서도 그대로다 — 시작점이 안 보이면
        // 올라갈 곳도 안 보인다.
        attach(bob, snu)

        mockMvc.perform(get("/api/v1/rankings").param("affiliationId", snu.toString()))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].score").value(0))
            .andExpect(jsonPath("$.content[0].rank").value(1))
    }

    @Test
    fun `소속을 떼면 그 랭킹에서 사라진다`() {
        // "어제까지 1등이던 사람이 오늘 없다" 가 생기지만, **"지금 그곳 사람들의 순위"
        // 라는 뜻은 유지된다** (기획서 4절).
        accept(bob, 1)
        attach(bob, snu)

        jdbcClient.sql("DELETE FROM user_affiliations WHERE user_id = :u").param("u", bob).update()

        mockMvc.perform(get("/api/v1/rankings").param("affiliationId", snu.toString()))
            .andExpect(jsonPath("$.totalElements").value(0))
    }

    @Test
    fun `아무도 없는 소속은 빈 순위표다`() {
        // 400 이 아니다 — 소속은 있는데 아직 아무도 안 붙었을 뿐이다.
        mockMvc.perform(get("/api/v1/rankings").param("affiliationId", kakao.toString()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(0))
    }

    @Test
    fun `없는 소속을 물으면 빈 순위표다`() {
        // 존재를 알려 주지 않는다. 소속 목록은 어드민만 본다 (#397).
        mockMvc.perform(get("/api/v1/rankings").param("affiliationId", "999999"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(0))
    }

    @Test
    fun `탈퇴한 사람은 소속 랭킹에서도 빠진다`() {
        // 전체 순위표의 규칙(#207)이 여기서도 같다 — 없는 사람이 순위에 있으면
        // 눌렀을 때 갈 곳이 없다.
        accept(bob, 1)
        attach(bob, snu)
        jdbcClient.sql("UPDATE users SET withdrawn_at = now() WHERE id = :id").param("id", bob).update()

        mockMvc.perform(get("/api/v1/rankings").param("affiliationId", snu.toString()))
            .andExpect(jsonPath("$.totalElements").value(0))
    }

    private fun user(email: String, nickname: String): Long =
        userRepository.save(User(email, "x", nickname, setOf(UserRole.USER))).id

    private fun affiliation(name: String): Long =
        jdbcClient.sql("INSERT INTO affiliations (name, kind) VALUES (:n, 'SCHOOL') RETURNING id")
            .param("n", name).query(Long::class.java).single()

    private fun attach(userId: Long, affiliationId: Long) {
        val emailId = jdbcClient.sql(
            "INSERT INTO user_emails (user_id, email) VALUES (:u, :e) RETURNING id",
        ).param("u", userId).param("e", "u$userId-a$affiliationId@x.ac.kr").query(Long::class.java).single()
        jdbcClient.sql(
            "INSERT INTO user_affiliations (user_id, affiliation_id, user_email_id) VALUES (:u, :a, :e)",
        ).param("u", userId).param("a", affiliationId).param("e", emailId).update()
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
