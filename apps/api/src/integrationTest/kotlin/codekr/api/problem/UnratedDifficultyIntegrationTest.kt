package codekr.api.problem

import codekr.api.queue.message.JudgeEventMessage
import codekr.api.ranking.repository.UserProblemScoreRepository
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
import kotlin.test.assertEquals

/**
 * 미평가·평가안함 난이도 (#195).
 *
 * **문제 수에는 세고 점수에는 넣지 않는다.** 푼 사람 입장에서 "푼 문제" 인 것은 사실이고,
 * 점수는 난이도에서 나오는데 그 근거가 없을 뿐이다.
 */
class UnratedDifficultyIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var jdbcClient: JdbcClient
    @Autowired private lateinit var recorder: JudgeResultRecorder
    @Autowired private lateinit var scoreRepository: UserProblemScoreRepository

    private var userId: Long = 0

    @BeforeEach
    fun setUp() {
        userId = userRepository.save(User("solver@codekr.dev", "x", "풀이왕", setOf(UserRole.USER))).id
    }

    @Test
    fun `미평가 문제를 풀면 문제 수는 늘고 점수는 늘지 않는다`() {
        problem(1, state = "UNRATED", level = null)

        accept(userId, 1)

        // 점수 0, 푼 문제 1 — **행은 남는다.**
        assertEquals(0 to 1, scoreRepository.totalsOf(userId))
    }

    @Test
    fun `평가안함 문제도 마찬가지다`() {
        problem(1, state = "NO_RATE", level = null)

        accept(userId, 1)

        assertEquals(0 to 1, scoreRepository.totalsOf(userId))
    }

    @Test
    fun `난이도가 매겨지면 이미 푼 사람의 점수가 그때 올라간다`() {
        // #194 가 만든 재계산 경로 위에 선다.
        problem(1, state = "UNRATED", level = null)
        accept(userId, 1)
        assertEquals(0 to 1, scoreRepository.totalsOf(userId))

        jdbcClient.sql(
            "UPDATE problems SET difficulty_level = 11, difficulty_state = 'RATED' WHERE id = 1",
        ).update()
        scoreRepository.recomputeAll(userId)

        // 골드 5 = 레벨 11 = 93점.
        assertEquals(93 to 1, scoreRepository.totalsOf(userId))
    }

    @Test
    fun `난이도순 정렬에서 두 상태는 양쪽 모두 뒤로 간다`() {
        problem(1, state = "RATED", level = 11)
        problem(2, state = "UNRATED", level = null)
        problem(3, state = "RATED", level = 1)

        // 쉬운순: 1(레벨1) → 11 → 미평가
        mockMvc.perform(get("/api/v1/problems").param("sort", "DIFFICULTY"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].id").value(3))
            .andExpect(jsonPath("$.content[2].id").value(2))

        // 어려운순에서도 미평가가 뒤다. **SQL 기본은 NULLS FIRST 라 그냥 두면 맨 앞이 된다.**
        mockMvc.perform(get("/api/v1/problems").param("sort", "DIFFICULTY_DESC"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].id").value(1))
            .andExpect(jsonPath("$.content[2].id").value(2))
    }

    @Test
    fun `티어 필터는 미평가를 잡지 않고 상태 필터가 잡는다`() {
        problem(1, state = "RATED", level = 1)
        problem(2, state = "UNRATED", level = null)

        // 브론즈로 걸면 미평가는 빠진다 — 레벨이 없어 어느 구간에도 안 든다.
        mockMvc.perform(get("/api/v1/problems").param("tier", "BRONZE"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value(1))

        // 고를 방법이 따로 있어야 목록에서 사라지지 않는다.
        mockMvc.perform(get("/api/v1/problems").param("difficultyState", "UNRATED"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value(2))
    }

    @Test
    fun `목록과 상세가 상태를 알려주고 표기를 준다`() {
        problem(1, state = "NO_RATE", level = null)

        mockMvc.perform(get("/api/v1/problems"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].difficulty").doesNotExist())
            .andExpect(jsonPath("$.content[0].difficultyState").value("NO_RATE"))
            // 화면이 상태별 문구를 다시 만들지 않게 표기는 늘 준다.
            .andExpect(jsonPath("$.content[0].difficultyLabel").value("평가 안 함"))
    }

    @Test
    fun `프로필 난이도 분포가 미평가에서 터지지 않는다`() {
        // 전에는 `Difficulty.ofLevel` 이 범위 밖에서 예외를 던져 화면 전체가 죽었다.
        problem(1, state = "UNRATED", level = null)
        problem(2, state = "RATED", level = 1)
        accept(userId, 1)
        accept(userId, 2)

        mockMvc.perform(get("/api/v1/users/풀이왕").header("Authorization", "Bearer ${token()}"))
            .andExpect(status().isOk)
            // 난이도가 없는 문제는 어느 막대에도 넣지 않는다 — 합계가 푼 문제 수와 다를 수 있다.
            .andExpect(jsonPath("$.solvedByTier.length()").value(1))
            .andExpect(jsonPath("$.solvedByTier[0].tier").value("BRONZE"))
            .andExpect(jsonPath("$.solvedCount").value(2))
    }

    private fun token(): String = jwt()

    private fun jwt(): String {
        val user = userRepository.findById(userId).orElseThrow()
        return tokenProvider.issueAccessToken(user)
    }

    @Autowired private lateinit var tokenProvider: codekr.api.auth.security.JwtTokenProvider

    private fun problem(id: Long, state: String, level: Int?) {
        jdbcClient.sql(
            """
            INSERT INTO problems (id, slug, title, category, difficulty_level, difficulty_state, description, published)
            VALUES (:id, 'p-' || :id, '문제 ' || :id, 'ALGORITHM', :level, :state, '설명', true)
            """,
        )
            .param("id", id)
            .param("level", level)
            .param("state", state)
            .update()
    }

    private fun accept(userId: Long, problemId: Long) {
        val submissionId = jdbcClient.sql(
            """
            INSERT INTO submissions
                (user_id, problem_id, runtime_id, source_code, status, kind, created_at, updated_at)
            VALUES (:userId, :problemId, 'python:3.12', 'print(3)', 'PENDING', 'USER', now(), now())
            RETURNING id
            """,
        )
            .param("userId", userId)
            .param("problemId", problemId)
            .query(Long::class.java)
            .single()

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
