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
 * 점수 변화 기록 (#476).
 *
 * **지금이 얼마인지만 보이고 어떻게 왔는지가 없었다.** 그리고 다시 계산하는 방식은
 * 난이도가 바뀌면(#194) 과거가 흔들리므로, **그때의 값을 기록**한다.
 */
class ScoreHistoryIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var jdbcClient: JdbcClient
    @Autowired private lateinit var recorder: JudgeResultRecorder

    private var userId: Long = 0
    private lateinit var handle: String

    @BeforeEach
    fun setUp() {
        val user = userRepository.save(User("solver@codekr.dev", "x", "풀이왕", setOf(UserRole.USER)))
        userId = user.id
        handle = user.handle
        (1..3).forEach { seq ->
            jdbcClient.sql(
                """
                INSERT INTO problems (id, slug, title, category, description, published,
                                      difficulty_level, difficulty_state)
                VALUES (:id, 'p-' || :id, '문제', 'ALGORITHM', '설명', true, 10, 'RATED')
                """,
            ).param("id", seq.toLong()).update()
        }
    }

    @Test
    fun `맞히면 그날의 점수가 남는다`() {
        accept(problemId = 1)

        val rows = jdbcClient.sql("SELECT score, tier_level FROM user_score_history WHERE user_id = :u")
            .param("u", userId).query { rs, _ -> rs.getInt("score") to rs.getInt("tier_level") }.list()
        assert(rows.size == 1 && rows[0].first > 0) { "그날의 점수가 남아야 합니다: $rows" }
    }

    @Test
    fun `같은 날 여러 번 맞혀도 하루 한 점이다`() {
        // 잘게 남기면 표만 커진다. 그래프로 읽을 때 하루보다 잘게 필요한 적이 없다.
        accept(problemId = 1)
        accept(problemId = 2)
        accept(problemId = 3)

        val count = jdbcClient.sql("SELECT count(*) FROM user_score_history WHERE user_id = :u")
            .param("u", userId).query(Int::class.java).single()
        assert(count == 1) { "하루에 한 행이어야 합니다: ${count}행" }
    }

    @Test
    fun `마지막 점수가 남는다`() {
        accept(problemId = 1)
        val first = scoreOf()
        accept(problemId = 2)

        assert(scoreOf() > first) { "같은 날 오르면 덮어써야 합니다: $first → ${scoreOf()}" }
    }

    @Test
    fun `남의 프로필에서도 보인다`() {
        // 활동 그래프(#117)가 이미 그렇고, 점수·티어는 랭킹(#57)이 이미 공개한다.
        accept(problemId = 1)

        mockMvc.perform(get("/api/v1/users/$handle/score-history"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].tierLevel").isNotEmpty)
    }

    @Test
    fun `아직 못 푼 사람의 화면이 깨지지 않는다`() {
        // #391 이 열려는 0점 사용자. 빈 목록이지 오류가 아니다.
        mockMvc.perform(get("/api/v1/users/$handle/score-history"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    private fun scoreOf(): Int =
        jdbcClient.sql("SELECT score FROM user_score_history WHERE user_id = :u")
            .param("u", userId).query(Int::class.java).single()

    private fun accept(problemId: Long) {
        val submissionId = jdbcClient.sql(
            """
            INSERT INTO submissions (user_id, problem_id, runtime_id, source_code, status, kind)
            VALUES (:u, :p, 'python:3.13', 'print(3)', 'PENDING', 'USER') RETURNING id
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
