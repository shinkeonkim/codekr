package codekr.api.problem

import codekr.api.auth.security.JwtTokenProvider
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 난이도 투표 (#477).
 *
 * **사용자가 사이트 자체를 낫게 만드는 첫 통로다.** 그래서 두 가지가 특히 중요하다 —
 * 아무나 매기지 못하게 하는 것과, 모인 숫자가 **먼저 온 몇 표의 메아리**가 되지 않게
 * 하는 것.
 */
class DifficultyVoteIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var jdbcClient: JdbcClient

    private var problemId: Long = 0
    private val tokens = mutableMapOf<String, String>()

    @BeforeEach
    fun setUp() {
        problemId = jdbcClient.sql(
            """
            INSERT INTO problems (slug, title, category, description, published, difficulty_state)
            VALUES ('vote-me', '투표 문제', 'ALGORITHM', '설명', true, 'UNRATED')
            RETURNING id
            """,
        ).query(Long::class.java).single()
    }

    @Test
    fun `푼 사람만 난이도를 매길 수 있다`() {
        // 안 풀고 매기는 것은 뜻이 약하다. 못 푼 사람의 체감은 정답률(#84)이 말한다.
        val token = user("nosolve")

        vote(token, 10)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("푼 사람만")))
    }

    @Test
    fun `투표하면 내 표가 남는다`() {
        val token = solver("kim")

        vote(token, 12)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.myLevel").value(12))
            .andExpect(jsonPath("$.voteCount").value(1))
    }

    @Test
    fun `표를 바꿀 수 있다`() {
        // 문제가 고쳐지면 옛 판단이 지금의 문제를 말하지 않게 된다.
        val token = solver("kim")
        vote(token, 12).andExpect(status().isOk)

        vote(token, 8)
            .andExpect(jsonPath("$.myLevel").value(8))
            .andExpect(jsonPath("$.voteCount").value(1))
    }

    @Test
    fun `투표하기 전에는 분포가 보이지 않는다`() {
        /*
          먼저 보면 뒤에 오는 사람이 끌려간다 — 그러면 모인 숫자는 문제의 난이도가 아니라
          **처음 몇 표의 메아리**가 된다.
        */
        val voter = solver("kim")
        vote(voter, 12).andExpect(status().isOk)
        val watcher = solver("lee")

        mockMvc.perform(get("/api/v1/problems/vote-me/difficulty-vote").header("Authorization", "Bearer $watcher"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.voteCount").value(1))
            .andExpect(jsonPath("$.medianLevel").doesNotExist())
    }

    @Test
    fun `표가 모이면 미평가 문제에 난이도가 붙는다`() {
        // #195 의 `UNRATED` 는 "아직 정하지 않았다" 는 자리다. 투표가 그 자리를 채운다.
        listOf("kim" to 10, "lee" to 14, "park" to 12).forEach { (name, level) ->
            vote(solver(name), level).andExpect(status().isOk)
        }

        val level = jdbcClient.sql("SELECT difficulty_level FROM problems WHERE id = :id")
            .param("id", problemId).query(Int::class.java).optional().orElse(null)
        assert(level == 12) { "중앙값이 난이도가 되어야 합니다: $level" }

        mockMvc.perform(get("/api/v1/problems/vote-me"))
            .andExpect(jsonPath("$.difficultyState").value("RATED"))
    }

    @Test
    fun `난이도가 붙으면 이미 맞힌 사람의 점수가 따라온다`() {
        /*
          #194 가 드러낸 자리다. 투표로 난이도가 움직이면 그 어긋남이 매번 난다 —
          어드민이 가끔 바꾸던 때보다 자주.
        */
        listOf("kim" to 10, "lee" to 14, "park" to 12).forEach { (name, level) ->
            vote(solver(name), level).andExpect(status().isOk)
        }

        val scores = jdbcClient.sql("SELECT DISTINCT score FROM user_problem_scores WHERE problem_id = :id")
            .param("id", problemId).query(Int::class.java).list()
        assert(scores.isNotEmpty() && scores.none { it == 0 }) {
            "난이도가 붙었으면 점수가 다시 계산되어야 합니다: $scores"
        }
    }

    @Test
    fun `어드민이 정한 난이도는 투표가 덮지 않는다`() {
        // 표가 적을 때 흔들리면 난이도가 무엇을 뜻하는지 알 수 없게 된다.
        jdbcClient.sql("UPDATE problems SET difficulty_level = 5, difficulty_state = 'RATED' WHERE id = :id")
            .param("id", problemId).update()

        listOf("kim" to 20, "lee" to 22, "park" to 21).forEach { (name, level) ->
            vote(solver(name), level).andExpect(status().isOk)
        }

        val level = jdbcClient.sql("SELECT difficulty_level FROM problems WHERE id = :id")
            .param("id", problemId).query(Int::class.java).single()
        assert(level == 5) { "어드민이 정한 난이도가 유지되어야 합니다: $level" }
    }

    private fun vote(token: String, level: Int) = mockMvc.perform(
        post("/api/v1/problems/vote-me/difficulty-vote")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"level":$level}"""),
    )

    private fun user(name: String): String = tokens.getOrPut(name) {
        tokenProvider.issueAccessToken(
            userRepository.save(User("$name@codekr.dev", "x", name, setOf(UserRole.USER))),
        )
    }

    /**
     * 그 문제를 푼 사람. 랭킹 점수 표가 곧 "푼 기록" 이다 (#57).
     *
     * **맞은 제출도 함께 만든다.** 점수 행만 넣으면 재계산(#194)이 "자격 없음" 으로 보고
     * 지운다 — 실제로 그렇게 동작하는 것이 맞고, 시험이 그것을 몰라서 처음에 깨졌다.
     */
    private fun solver(name: String): String {
        val token = user(name)
        val userId = userRepository.findByNickname(name)!!.id
        jdbcClient.sql(
            """
            INSERT INTO submissions (user_id, problem_id, runtime_id, source_code, status, verdict, kind)
            VALUES (:u, :p, 'python:3.13', 'print(1)', 'COMPLETED', 'ACCEPTED', 'USER')
            """,
        ).param("u", userId).param("p", problemId).update()
        jdbcClient.sql(
            """
            INSERT INTO user_problem_scores (user_id, problem_id, score, solved_at)
            VALUES (:u, :p, 0, now()) ON CONFLICT DO NOTHING
            """,
        ).param("u", userId).param("p", problemId).update()
        return token
    }
}
