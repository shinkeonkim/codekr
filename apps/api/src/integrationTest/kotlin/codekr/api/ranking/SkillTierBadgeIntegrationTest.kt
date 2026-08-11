package codekr.api.ranking

import codekr.api.auth.security.JwtTokenProvider
import codekr.api.queue.message.JudgeEventMessage
import codekr.api.ranking.badge.Badge
import codekr.api.ranking.entity.SkillTier
import codekr.api.submission.entity.Verdict
import codekr.api.submission.service.JudgeResultRecorder
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 실력 티어·뱃지·랭킹 비참여 (#58). */
class SkillTierBadgeIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var jdbcClient: JdbcClient
    @Autowired private lateinit var recorder: JudgeResultRecorder

    private var userId: Long = 0
    private var rivalId: Long = 0
    private lateinit var token: String

    @BeforeEach
    fun setUp() {
        val user = userRepository.save(User("solver@codekr.dev", "x", "풀이왕", setOf(UserRole.USER)))
        userId = user.id
        token = tokenProvider.issueAccessToken(user)
        rivalId = userRepository.save(User("rival@codekr.dev", "x", "라이벌", setOf(UserRole.USER))).id
    }

    @Test
    fun `티어 이름은 6단계 5스텝으로 읽힌다`() {
        assertEquals("브론즈 5", SkillTier.nameOf(1))
        assertEquals("브론즈 1", SkillTier.nameOf(5))
        assertEquals("실버 5", SkillTier.nameOf(6))
        assertEquals("루비 1", SkillTier.nameOf(30))
    }

    @Test
    fun `한 문제도 못 풀었으면 티어가 없다`() {
        // 브론즈 5 로 시작시키면 '아직 시작하지 않음'과 '가장 낮음'이 구분되지 않는다.
        assertNull(SkillTier.of(0))
    }

    @Test
    fun `티어는 점수가 내려가도 떨어지지 않는다`() {
        problem(id = 1, level = 20)
        val id = insert(userId, 1)
        judge(id, Verdict.ACCEPTED)
        val before = tierName()

        // 재채점으로 점수가 0 이 된다 (#107).
        judge(id, Verdict.WRONG_ANSWER)

        // 강등은 없다. 우리 잘못(잘못된 테스트케이스)으로 남의 티어를 깎아서는 안 된다.
        mockMvc.perform(get("/api/v1/users/풀이왕").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.score").value(0))
            .andExpect(jsonPath("$.skillTier.name").value(before))
    }

    @Test
    fun `첫 정답과 최초 해결자 뱃지를 준다`() {
        problem(id = 1, level = 1)
        accept(userId, 1)

        val badges = badgeCodes(userId)
        assertTrue(Badge.FIRST_ACCEPT.code in badges)
        assertTrue(Badge.FIRST_SOLVER.code in badges, "아무도 안 푼 문제를 처음 풀었습니다")
    }

    @Test
    fun `나중에 푼 사람은 최초 해결자가 아니다`() {
        problem(id = 1, level = 1)
        accept(userId, 1, at = "2026-01-01T00:00:00Z")
        accept(rivalId, 1, at = "2026-02-01T00:00:00Z")

        assertTrue(Badge.FIRST_SOLVER.code in badgeCodes(userId))
        assertTrue(Badge.FIRST_SOLVER.code !in badgeCodes(rivalId))
    }

    @Test
    fun `카테고리 10문제를 채우면 뱃지를 준다`() {
        repeat(Badge.CATEGORY_THRESHOLD) { i ->
            problem(id = (i + 1).toLong(), level = 1)
            accept(userId, (i + 1).toLong())
        }

        assertTrue("CATEGORY_10_ALGORITHM" in badgeCodes(userId))
    }

    @Test
    fun `아홉 문제로는 카테고리 뱃지를 주지 않는다`() {
        repeat(Badge.CATEGORY_THRESHOLD - 1) { i ->
            problem(id = (i + 1).toLong(), level = 1)
            accept(userId, (i + 1).toLong())
        }

        assertTrue("CATEGORY_10_ALGORITHM" !in badgeCodes(userId))
    }

    @Test
    fun `랭킹을 끄면 목록에서 빠지지만 점수는 남는다`() {
        problem(id = 1, level = 11)
        accept(userId, 1)
        mockMvc.perform(get("/api/v1/rankings")).andExpect(jsonPath("$.content.length()").value(1))

        mockMvc.perform(
            patch("/api/v1/users/me/settings")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"rankingOptOut":true}"""),
        ).andExpect(status().isOk).andExpect(jsonPath("$.rankingOptOut").value(true))

        mockMvc.perform(get("/api/v1/rankings"))
            .andExpect(jsonPath("$.content.length()").value(0))
            .andExpect(jsonPath("$.totalElements").value(0))

        // 점수는 그대로다 — 껐다 켤 때 기록이 사라지면 끄기가 되돌릴 수 없는 선택이 된다.
        mockMvc.perform(get("/api/v1/users/풀이왕").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.score").value(93))
            .andExpect(jsonPath("$.rank").doesNotExist())
    }

    @Test
    fun `월간 랭킹은 이번 달에 푼 것만 센다`() {
        problem(id = 1, level = 11)
        problem(id = 2, level = 11)
        accept(userId, 1, at = "2020-01-01T00:00:00Z")
        accept(rivalId, 2)

        mockMvc.perform(get("/api/v1/rankings").param("period", "MONTHLY"))
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].nickname").value("라이벌"))

        mockMvc.perform(get("/api/v1/rankings").param("period", "ALL_TIME"))
            .andExpect(jsonPath("$.content.length()").value(2))
    }

    @Test
    fun `프로필이 실력 티어와 순위를 보여준다`() {
        problem(id = 1, level = 20)
        accept(userId, 1)

        mockMvc.perform(get("/api/v1/users/풀이왕").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.rank").value(1))
            .andExpect(jsonPath("$.skillTier.name").exists())
            // 다음 티어까지 얼마가 남았는지 없으면 숫자가 목표가 되지 못한다.
            .andExpect(jsonPath("$.skillTier.nextLevelScore").isNumber)
            .andExpect(jsonPath("$.badges[?(@.code == 'FIRST_ACCEPT')].label").value("첫 정답"))
    }

    @Test
    fun `랭킹 화면이 고를 축을 서버가 알려준다`() {
        mockMvc.perform(get("/api/v1/rankings/metrics"))
            .andExpect(jsonPath("$.metrics.length()").value(2))
            .andExpect(jsonPath("$.periods.length()").value(2))
            .andExpect(jsonPath("$.periods[0].value").value("ALL_TIME"))
    }

    private fun tierName(): String =
        jdbcClient.sql("SELECT peak_score FROM users WHERE id = :id")
            .param("id", userId).query(Int::class.java).single()
            .let { requireNotNull(SkillTier.of(it)).name }

    private fun badgeCodes(userId: Long): List<String> =
        jdbcClient.sql("SELECT code FROM user_badges WHERE user_id = :id")
            .param("id", userId).query(String::class.java).list().filterNotNull()

    private fun problem(id: Long, level: Int) {
        jdbcClient.sql(
            """
            INSERT INTO problems (id, slug, title, category, difficulty_level, description, published)
            VALUES (:id, 'p-' || :id, '문제 ' || :id, 'ALGORITHM', :level, '설명', true)
            """,
        ).param("id", id).param("level", level).update()
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
            .param("userId", userId).param("problemId", problemId).param("at", at)
            .query(Long::class.java).single()

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
