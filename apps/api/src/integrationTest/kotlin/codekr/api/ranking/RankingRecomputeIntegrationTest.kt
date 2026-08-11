package codekr.api.ranking

import codekr.api.ranking.repository.UserProblemScoreRepository
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 랭킹을 붙이기 전의 제출을 되살린다 (#177).
 *
 * **여기서 재현하는 상황이 실제로 일어난 것이다** — 기능을 붙인 뒤 랭킹이 비어 있었다.
 * 그 전의 제출은 `ScoreRecorder` 를 거치지 않아 점수 표에 아무것도 남기지 않는다.
 */
class RankingRecomputeIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var jdbcClient: JdbcClient
    @Autowired private lateinit var scoreRepository: UserProblemScoreRepository
    @Autowired private lateinit var recomputeService: RankingRecomputeService

    private var userId: Long = 0
    private var rivalId: Long = 0

    @BeforeEach
    fun setUp() {
        userId = userRepository.save(User("solver@codekr.dev", "x", "풀이왕", setOf(UserRole.USER))).id
        rivalId = userRepository.save(User("rival@codekr.dev", "x", "라이벌", setOf(UserRole.USER))).id
    }

    @Test
    fun `기능 도입 전의 제출은 랭킹에 없고, 재계산으로 되살아난다`() {
        problem(id = 1, level = 11)
        acceptedBeforeRanking(userId, problemId = 1)

        // 이것이 사용자가 본 화면이다 — 제출은 분명히 있는데 랭킹이 비어 있다.
        mockMvc.perform(get("/api/v1/rankings"))
            .andExpect(jsonPath("$.totalElements").value(0))

        assertEquals(1, recomputeService.recomputeEveryone())

        mockMvc.perform(get("/api/v1/rankings"))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].nickname").value("풀이왕"))
            .andExpect(jsonPath("$.content[0].score").value(93))
    }

    @Test
    fun `재계산이 최고 점수와 뱃지까지 맞춘다`() {
        // 점수만 되살리면 실력 티어는 계속 비어 있다 — peak_score 로 정하기 때문이다 (#58).
        problem(id = 1, level = 11)
        acceptedBeforeRanking(userId, problemId = 1)

        recomputeService.recompute(userId)

        assertEquals(93, userRepository.findById(userId).orElseThrow().peakScore)
        assertTrue(badgeCodes(userId).contains("FIRST_ACCEPT"), "첫 정답 뱃지가 없습니다")
        assertTrue(badgeCodes(userId).contains("FIRST_SOLVER"), "최초 해결 뱃지가 없습니다")
    }

    @Test
    fun `맞힌 제출이 없는 사용자는 대상에서 빠진다`() {
        problem(id = 1, level = 11)
        acceptedBeforeRanking(userId, problemId = 1)
        // 라이벌은 틀렸다. 대상에 넣으면 사용자가 늘수록 헛일이 늘어난다.
        insert(rivalId, problemId = 1, verdict = "WRONG_ANSWER")

        assertEquals(1, recomputeService.recomputeEveryone())
        assertEquals(0 to 0, scoreRepository.totalsOf(rivalId))
    }

    @Test
    fun `여러 번 불러도 결과가 같다`() {
        // 운영에서 두 번 누르는 일은 반드시 생긴다.
        problem(id = 1, level = 11)
        acceptedBeforeRanking(userId, problemId = 1)

        val first = recomputeService.recompute(userId)
        val second = recomputeService.recompute(userId)

        assertEquals(first, second)
        assertEquals(badgeCodes(userId), badgeCodes(userId))
    }

    private fun badgeCodes(userId: Long): List<String> =
        jdbcClient.sql("SELECT code FROM user_badges WHERE user_id = :id ORDER BY code")
            .param("id", userId)
            .query { rs, _ -> rs.getString("code") }
            .list()

    private fun problem(id: Long, level: Int) {
        jdbcClient.sql(
            """
            INSERT INTO problems (id, slug, title, category, difficulty_level, description, published)
            VALUES (:id, 'p-' || :id, '문제 ' || :id, 'ALGORITHM', :level, '설명', true)
            """,
        ).param("id", id).param("level", level).update()
    }

    /**
     * 채점기를 거치지 않고 정답 제출만 남긴다.
     *
     * **`JudgeResultRecorder` 를 부르지 않는 것이 핵심이다.** 그것을 부르면 점수가
     * 곧바로 기록돼서 이 시험이 재현하려는 상황이 사라진다.
     */
    private fun acceptedBeforeRanking(userId: Long, problemId: Long) =
        insert(userId, problemId, verdict = "ACCEPTED")

    private fun insert(userId: Long, problemId: Long, verdict: String) {
        jdbcClient.sql(
            """
            INSERT INTO submissions
                (user_id, problem_id, runtime_id, source_code, status, verdict, kind, created_at, updated_at)
            VALUES (:userId, :problemId, 'python:3.12', 'print(3)', 'COMPLETED', :verdict, 'USER', now(), now())
            """,
        )
            .param("userId", userId)
            .param("problemId", problemId)
            .param("verdict", verdict)
            .update()
    }
}
