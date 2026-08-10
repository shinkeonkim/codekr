package codekr.api.ranking

import codekr.api.queue.message.JudgeEventMessage
import codekr.api.ranking.entity.ProblemScore
import codekr.api.ranking.entity.SCORE_PROBLEM_LIMIT
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
import kotlin.test.assertTrue

/** 랭킹 점수와 순위 (#57, #85). */
class RankingIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var jdbcClient: JdbcClient
    @Autowired private lateinit var recorder: JudgeResultRecorder
    @Autowired private lateinit var scoreRepository: UserProblemScoreRepository

    private var userId: Long = 0
    private var rivalId: Long = 0

    @BeforeEach
    fun setUp() {
        userId = userRepository.save(User("solver@codekr.dev", "x", "풀이왕", setOf(UserRole.USER))).id
        rivalId = userRepository.save(User("rival@codekr.dev", "x", "라이벌", setOf(UserRole.USER))).id
    }

    @Test
    fun `문제 점수는 난이도에 따라 커진다`() {
        // 기획서에 못 박은 값이다. 바뀌면 모두의 점수가 바뀌므로 시험으로 고정한다.
        assertEquals(10, ProblemScore.of(1))
        assertEquals(31, ProblemScore.of(6))
        assertEquals(93, ProblemScore.of(11))
        assertEquals(6462, ProblemScore.of(30))
    }

    @Test
    fun `맞히면 점수가 오르고 같은 문제를 다시 맞혀도 오르지 않는다`() {
        problem(id = 1, level = 11)

        accept(userId, problemId = 1)
        assertEquals(93 to 1, scoreRepository.totalsOf(userId))

        // 같은 문제를 또 맞힌다. 최초 1회만 점수를 준다.
        accept(userId, problemId = 1)
        assertEquals(93 to 1, scoreRepository.totalsOf(userId))
    }

    @Test
    fun `오답은 점수를 주지도 깎지도 않는다`() {
        problem(id = 1, level = 11)

        judge(insert(userId, 1), Verdict.WRONG_ANSWER)
        assertEquals(0 to 0, scoreRepository.totalsOf(userId))

        accept(userId, problemId = 1)
        judge(insert(userId, 1), Verdict.WRONG_ANSWER)

        // 맞힌 뒤 틀려도 점수는 그대로다 — 감점은 제출을 두려워하게 만든다.
        assertEquals(93 to 1, scoreRepository.totalsOf(userId))
    }

    @Test
    fun `재채점으로 정답이 뒤집히면 점수가 내려간다`() {
        problem(id = 1, level = 11)
        val id = insert(userId, 1)
        judge(id, Verdict.ACCEPTED)
        assertEquals(93 to 1, scoreRepository.totalsOf(userId))

        // 잘못된 테스트케이스로 통과했던 풀이가 오답이 된다 (#107).
        judge(id, Verdict.WRONG_ANSWER)

        assertEquals(0 to 0, scoreRepository.totalsOf(userId), "점수는 내려갈 수 있어야 합니다")
    }

    @Test
    fun `공개되지 않은 문제는 점수를 주지 않는다`() {
        problem(id = 1, level = 11, published = false)

        accept(userId, problemId = 1)

        // 준비 중인 문제로 점수를 벌 수 있으면 출제자와 그 지인만 유리해진다.
        assertEquals(0 to 0, scoreRepository.totalsOf(userId))
    }

    @Test
    fun `쉬운 문제를 아무리 많이 풀어도 어려운 문제 하나를 이기지 못한다`() {
        // 상위 N개만 합산하는 규칙(#85)이 실제로 막는지 본다.
        repeat(SCORE_PROBLEM_LIMIT + 20) { i ->
            problem(id = (i + 1).toLong(), level = 1)
            accept(userId, problemId = (i + 1).toLong())
        }
        val grinder = scoreRepository.totalsOf(userId)

        problem(id = 900, level = 30)
        accept(rivalId, problemId = 900)
        val expert = scoreRepository.totalsOf(rivalId)

        assertEquals(SCORE_PROBLEM_LIMIT * 10, grinder.first, "상위 $SCORE_PROBLEM_LIMIT 개만 반영해야 합니다")
        assertTrue(expert.first > grinder.first, "루비 한 문제가 브론즈 ${grinder.second}문제보다 높아야 합니다")
        assertTrue(grinder.second > expert.second, "푼 문제 수는 반대여야 합니다")
    }

    @Test
    fun `지표를 바꾸면 순서가 뒤집힌다`() {
        problem(id = 1, level = 30)
        accept(rivalId, problemId = 1)
        repeat(3) { i ->
            problem(id = (10 + i).toLong(), level = 1)
            accept(userId, problemId = (10 + i).toLong())
        }

        mockMvc.perform(get("/api/v1/rankings").param("metric", "SCORE"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].nickname").value("라이벌"))
            .andExpect(jsonPath("$.content[0].rank").value(1))
            .andExpect(jsonPath("$.content[1].nickname").value("풀이왕"))

        mockMvc.perform(get("/api/v1/rankings").param("metric", "SOLVED_COUNT"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].nickname").value("풀이왕"))
            .andExpect(jsonPath("$.content[0].solvedCount").value(3))
    }

    @Test
    fun `한 문제도 못 푼 사람은 랭킹에 나오지 않는다`() {
        problem(id = 1, level = 11)
        accept(userId, problemId = 1)

        // 가입만 한 사람으로 목록을 채우면 랭킹이 회원 명부가 된다.
        mockMvc.perform(get("/api/v1/rankings"))
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.totalElements").value(1))
    }

    @Test
    fun `동점이면 먼저 푼 사람이 앞선다`() {
        problem(id = 1, level = 11)
        accept(rivalId, problemId = 1, at = "2026-01-01T00:00:00Z")
        accept(userId, problemId = 1, at = "2025-01-01T00:00:00Z")

        mockMvc.perform(get("/api/v1/rankings"))
            .andExpect(jsonPath("$.content[0].nickname").value("풀이왕"))
            // 동점 처리 규칙이 순서를 끝까지 정하므로 등수도 갈린다 — 공동 1위가 생기지 않는다.
            .andExpect(jsonPath("$.content[0].rank").value(1))
            .andExpect(jsonPath("$.content[1].rank").value(2))
    }

    @Test
    fun `망가진 점수를 재계산으로 되돌린다`() {
        problem(id = 1, level = 11)
        accept(userId, problemId = 1)

        jdbcClient.sql("UPDATE user_problem_scores SET score = 99999 WHERE user_id = :id")
            .param("id", userId).update()
        assertEquals(99999, scoreRepository.totalsOf(userId).first)

        scoreRepository.recomputeAll(userId)

        assertEquals(93 to 1, scoreRepository.totalsOf(userId))
    }

    @Test
    fun `지표 목록을 서버가 알려준다`() {
        mockMvc.perform(get("/api/v1/rankings/metrics"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].value").value("SCORE"))
            .andExpect(jsonPath("$[0].label").value("실력 점수"))
    }

    private fun problem(id: Long, level: Int, published: Boolean = true) {
        jdbcClient.sql(
            """
            INSERT INTO problems (id, slug, title, category, difficulty_level, description, published)
            VALUES (:id, 'p-' || :id, '문제 ' || :id, 'ALGORITHM', :level, '설명', :published)
            """,
        ).param("id", id).param("level", level).param("published", published).update()
    }

    private fun accept(userId: Long, problemId: Long, at: String? = null) =
        judge(insert(userId, problemId, at), Verdict.ACCEPTED)

    private fun insert(userId: Long, problemId: Long, at: String? = null): Long =
        jdbcClient.sql(
            """
            INSERT INTO submissions
                (user_id, problem_id, runtime_id, source_code, status, kind, created_at, updated_at)
            VALUES (:userId, :problemId, 'python:3.12', 'print(3)', 'PENDING', 'USER',
                    coalesce(:at::timestamptz, now()), now())
            RETURNING id
            """,
        )
            .param("userId", userId)
            .param("problemId", problemId)
            .param("at", at)
            .query(Long::class.java)
            .single()

    private fun judge(submissionId: Long, verdict: Verdict) {
        recorder.record(
            JudgeEventMessage(
                type = JudgeEventMessage.TYPE_COMPLETED,
                submissionId = submissionId,
                verdict = verdict.name,
                passedCount = if (verdict == Verdict.ACCEPTED) 1 else 0,
                totalCount = 1,
            ),
        )
    }
}
