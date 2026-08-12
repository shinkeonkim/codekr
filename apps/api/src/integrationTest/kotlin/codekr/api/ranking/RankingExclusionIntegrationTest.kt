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
 * 랭킹에서 빠지는 사람 (#207).
 *
 * **탈퇴한 사람만 빠진다.** 없는 사람이 순위에 있으면 눌렀을 때 갈 곳이 없다.
 *
 * 전에는 어드민(#188)과 비참여 설정(#41)도 걸렀는데 둘 다 걷어냈다 — 빠진 사람이 있는
 * 순위에서 "3위" 는 무엇의 3위인지 알 수 없기 때문이다. 그래서 이 시험은 **어드민이
 * 랭킹에 나오는지**를 확인한다. 전과 정반대다.
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
    fun `관리 권한을 가진 계정도 랭킹에 오른다`() {
        // ADMIN_AREA 의 역할이면 무엇이든 나온다 — 모든 사용자가 노출된다는 것이 #207 이다.
        UserRole.ADMIN_AREA.forEachIndexed { index, role ->
            val id = userRepository.save(User("staff$index@codekr.dev", "x", "운영자$index", setOf(role))).id
            solve(id)
        }
        solve(solverId)

        mockMvc.perform(get("/api/v1/rankings"))
            .andExpect(jsonPath("$.totalElements").value(UserRole.ADMIN_AREA.size + 1))
    }

    @Test
    fun `탈퇴한 사람은 랭킹에서 빠진다`() {
        val leaverId = userRepository.save(User("leaver@codekr.dev", "x", "떠난이", setOf(UserRole.USER))).id
        solve(leaverId)
        solve(solverId)
        // 탈퇴는 서비스가 여러 값을 함께 지우지만, 랭킹이 보는 것은 이 한 컬럼이다.
        jdbcClient.sql("UPDATE users SET withdrawn_at = now() WHERE id = :id").param("id", leaverId).update()

        mockMvc.perform(get("/api/v1/rankings"))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].nickname").value("풀이왕"))
    }

    @Test
    fun `빠지는 사람은 내 순위 조회에서도 없다`() {
        // 목록에는 없는데 프로필의 '랭킹' 에는 있으면 두 화면이 서로 다른 말을 한다.
        // 세 질의가 같은 조건을 쓰는지 확인하는 자리다.
        val leaverId = userRepository.save(User("leaver@codekr.dev", "x", "떠난이", setOf(UserRole.USER))).id
        solve(leaverId)
        solve(solverId)
        jdbcClient.sql("UPDATE users SET withdrawn_at = now() WHERE id = :id").param("id", leaverId).update()

        assertNull(rankingService.rankOf("떠난이", RankingMetric.SCORE, RankingPeriod.ALL_TIME))
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
