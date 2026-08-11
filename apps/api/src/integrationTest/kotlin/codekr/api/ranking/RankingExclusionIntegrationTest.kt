package codekr.api.ranking

import codekr.api.ranking.entity.RankingMetric
import codekr.api.ranking.entity.RankingPeriod
import codekr.api.ranking.repository.UserProblemScoreRepository
import codekr.api.ranking.service.RankingService
import codekr.api.ranking.service.RankingRecomputeService
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 랭킹에서 빠지는 사람 (#188).
 *
 * **출제자는 정답을 이미 알고 있다.** 문제 검증 과정에서 정답 제출이 저절로 쌓이므로,
 * 같은 표에 놓으면 랭킹의 뜻이 흐려진다.
 */
class RankingExclusionIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var jdbcClient: JdbcClient
    @Autowired private lateinit var scoreRepository: UserProblemScoreRepository
    @Autowired private lateinit var recomputeService: RankingRecomputeService
    @Autowired private lateinit var rankingService: RankingService

    private var solverId: Long = 0

    @BeforeEach
    fun setUp() {
        solverId = userRepository.save(User("solver@codekr.dev", "x", "풀이왕", setOf(UserRole.USER))).id
        problem(id = 1, level = 11)
    }

    @Test
    fun `관리 권한을 가진 계정은 랭킹에 오르지 않는다`() {
        // ADMIN_AREA 의 역할이면 무엇이든 빠진다 — 어드민 화면을 볼 수 있는 사람과 같은 기준이다.
        UserRole.ADMIN_AREA.forEachIndexed { index, role ->
            val id = userRepository.save(User("staff$index@codekr.dev", "x", "운영자$index", setOf(role))).id
            solve(id)
        }
        solve(solverId)

        mockMvc.perform(get("/api/v1/rankings"))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].nickname").value("풀이왕"))
    }

    @Test
    fun `제외된 사람은 내 순위 조회에서도 없다`() {
        // 목록에는 없는데 프로필의 '랭킹' 에는 있으면 두 화면이 서로 다른 말을 한다.
        // 세 질의가 같은 조건을 쓰는지 확인하는 자리다.
        val adminId = userRepository.save(User("admin@codekr.dev", "x", "관리자", setOf(UserRole.ADMIN))).id
        solve(adminId)

        solve(solverId)

        assertNull(rankingService.rankOf("관리자", RankingMetric.SCORE, RankingPeriod.ALL_TIME))
        assertNotNull(rankingService.rankOf("풀이왕", RankingMetric.SCORE, RankingPeriod.ALL_TIME))
    }

    @Test
    fun `일반 회원은 그대로 오른다`() {
        solve(solverId)

        mockMvc.perform(get("/api/v1/rankings"))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].score").value(93))
    }

    private fun solve(userId: Long) {
        jdbcClient.sql(
            """
            INSERT INTO submissions
                (user_id, problem_id, runtime_id, source_code, status, verdict, kind, created_at, updated_at)
            VALUES (:userId, 1, 'python:3.12', 'print(3)', 'COMPLETED', 'ACCEPTED', 'USER', now(), now())
            """,
        ).param("userId", userId).update()
        recomputeService.recompute(userId)
        check(scoreRepository.totalsOf(userId).first > 0) { "점수가 기록되지 않았습니다" }
    }

    private fun problem(id: Long, level: Int) {
        jdbcClient.sql(
            """
            INSERT INTO problems (id, slug, title, category, difficulty_level, description, published)
            VALUES (:id, 'p-' || :id, '문제 ' || :id, 'ALGORITHM', :level, '설명', true)
            """,
        ).param("id", id).param("level", level).update()
    }
}
