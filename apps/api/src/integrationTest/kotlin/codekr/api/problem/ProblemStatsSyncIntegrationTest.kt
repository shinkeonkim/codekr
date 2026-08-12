package codekr.api.problem

import codekr.api.auth.security.JwtTokenProvider
import codekr.api.problem.repository.ProblemStatsSyncRepository
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
import kotlin.test.assertEquals

/**
 * 저장된 문제 통계 (#205).
 *
 * **저장하기로 한 이상 값이 따라오는지 확인해야 한다.** 특히 내려가는 경우 —
 * 증분으로 짜면 늘어나는 것은 맞고 줄어드는 것은 틀린다.
 */
class ProblemStatsSyncIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var jdbcClient: JdbcClient
    @Autowired private lateinit var recorder: JudgeResultRecorder
    @Autowired private lateinit var statsSync: ProblemStatsSyncRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider

    private var userId: Long = 0
    private var otherId: Long = 0

    @BeforeEach
    fun setUp() {
        userId = userRepository.save(User("solver@codekr.dev", "x", "풀이왕", setOf(UserRole.USER))).id
        otherId = userRepository.save(User("other@codekr.dev", "x", "구경꾼", setOf(UserRole.USER))).id
        problem(1)
    }

    @Test
    fun `채점이 끝나면 저장된 통계가 따라온다`() {
        accept(userId, 1)

        assertEquals(1 to 1, stored(1))
    }

    @Test
    fun `같은 사람이 여러 번 내도 한 명이다`() {
        // 제출 건수가 아니라 사람 수다.
        repeat(3) { accept(userId, 1) }
        judge(insert(otherId, 1), Verdict.WRONG_ANSWER)

        assertEquals(2 to 1, stored(1))
    }

    @Test
    fun `판정이 뒤집히면 정답자 수가 내려간다`() {
        val submissionId = insert(userId, 1)
        judge(submissionId, Verdict.ACCEPTED)
        assertEquals(1 to 1, stored(1))

        // 재채점으로 오답이 되는 경우 (#107). **증분으로 짜면 여기서 틀린다.**
        judge(submissionId, Verdict.WRONG_ANSWER)

        assertEquals(1 to 0, stored(1))
    }

    @Test
    fun `제출을 지우면 통계에서 빠진다`() {
        accept(userId, 1)
        jdbcClient.sql("UPDATE submissions SET deleted_at = now() WHERE user_id = :id").param("id", userId).update()

        // 지우는 경로는 갱신을 부르지 않는다 — 그래서 어긋난 것이 보여야 한다.
        val drift = statsSync.findDrift()
        assertEquals(listOf(1L), drift.map { it.problemId })

        // 다시 세면 맞는다.
        statsSync.refreshAll()
        assertEquals(0 to 0, stored(1))
    }

    @Test
    fun `많이 풀린 순으로 정렬한다`() {
        problem(2)
        accept(userId, 2)
        accept(otherId, 2)
        accept(userId, 1)

        mockMvc.perform(get("/api/v1/problems").param("sort", "SOLVERS_DESC"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].id").value(2))
            .andExpect(jsonPath("$.content[1].id").value(1))
    }

    @Test
    fun `제출자가 없는 문제는 정답률 정렬에서 뒤로 간다`() {
        // **0/0 은 정답률이 없는 것이지 0% 가 아니다.** 0 으로 보면 목록 맨 앞이나
        // 맨 뒤가 새 문제로만 찬다.
        problem(2) // 제출 없음
        judge(insert(userId, 1), Verdict.WRONG_ANSWER) // 정답률 0%

        mockMvc.perform(get("/api/v1/problems").param("sort", "ACCEPTANCE_ASC"))
            .andExpect(status().isOk)
            // 0% 인 1번이 먼저, 제출자가 없는 2번이 뒤.
            .andExpect(jsonPath("$.content[0].id").value(1))
            .andExpect(jsonPath("$.content[1].id").value(2))
    }

    @Test
    fun `정답률 범위로 거른다`() {
        problem(2)
        // 1번: 2명 중 1명 = 50%
        accept(userId, 1)
        judge(insert(otherId, 1), Verdict.WRONG_ANSWER)
        // 2번: 1명 중 1명 = 100%
        accept(userId, 2)

        mockMvc.perform(get("/api/v1/problems").param("acceptanceTo", "60"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value(1))
    }

    @Test
    fun `제출자가 없는 문제는 정답률 범위에 들지 않는다`() {
        // 0/0 은 0% 가 아니다 — 0~100 을 걸어도 걸리면 안 된다.
        problem(2)

        mockMvc.perform(get("/api/v1/problems").param("acceptanceFrom", "0").param("acceptanceTo", "100"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(0))
    }

    @Test
    fun `푼 사람 수 범위로 거른다`() {
        problem(2)
        accept(userId, 1)
        accept(otherId, 1)
        accept(userId, 2)

        mockMvc.perform(get("/api/v1/problems").param("solversFrom", "2"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value(1))
    }

    @Test
    fun `안 푼 것만 고른다`() {
        problem(2)
        accept(userId, 1)

        mockMvc.perform(
            get("/api/v1/problems").param("solved", "false")
                .header("Authorization", "Bearer ${token(userId)}"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value(2))
    }

    @Test
    fun `비로그인이 해결 여부를 넘기면 무시한다`() {
        // 누를 수 없는 필터를 만들지 않는 것은 화면의 몫이다. 서버는 조용히 넘긴다 —
        // 여기서 401 을 내면 공개 목록이 로그인해야 열리는 화면이 된다.
        problem(2)
        accept(userId, 1)

        mockMvc.perform(get("/api/v1/problems").param("solved", "false"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(2))
    }

    private fun token(userId: Long): String =
        tokenProvider.issueAccessToken(userRepository.findById(userId).orElseThrow())

    private fun stored(problemId: Long): Pair<Int, Int> =
        jdbcClient.sql("SELECT submitters, solvers FROM problem_stats WHERE problem_id = :id")
            .param("id", problemId)
            .query { rs, _ -> rs.getInt("submitters") to rs.getInt("solvers") }
            .optional()
            .orElse(0 to 0)

    private fun problem(id: Long) {
        jdbcClient.sql(
            """
            INSERT INTO problems (id, slug, title, category, difficulty_level, description, published)
            VALUES (:id, 'p-' || :id, '문제 ' || :id, 'ALGORITHM', 1, '설명', true)
            """,
        ).param("id", id).update()
    }

    private fun accept(userId: Long, problemId: Long) = judge(insert(userId, problemId), Verdict.ACCEPTED)

    private fun insert(userId: Long, problemId: Long): Long =
        jdbcClient.sql(
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
