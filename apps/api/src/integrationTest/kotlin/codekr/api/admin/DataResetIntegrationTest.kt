package codekr.api.admin

import codekr.api.admin.service.DataResetService
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
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals

/**
 * 데이터 초기화 (#285).
 *
 * **`CASCADE` 가 무엇까지 끌고 가는지는 목록을 보고 알 수 없다.** 외래키가 하나 늘면
 * 조용히 바뀌고, 그때 사라지는 것은 남기려던 표다. 그래서 눌러 보고 확인한다.
 */
@TestPropertySource(properties = ["codekr.data-reset.enabled=true"])
class DataResetIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var jdbcClient: JdbcClient

    private lateinit var superuserToken: String
    private lateinit var adminToken: String

    @BeforeEach
    fun setUp() {
        superuserToken = tokenProvider.issueAccessToken(
            userRepository.save(User("root@codekr.dev", "x", "최고관리자", setOf(UserRole.SUPERUSER))),
        )
        adminToken = tokenProvider.issueAccessToken(
            userRepository.save(User("admin@codekr.dev", "x", "관리자", setOf(UserRole.ADMIN))),
        )
    }

    @Test
    fun `문제와 제출을 비우고 시퀀스를 1 부터 돌린다`() {
        insertProblem()
        insertSubmission()

        reset()

        assertEquals(0, count("problems"))
        assertEquals(0, count("submissions"))
        // **다음 문제가 1번이어야 한다** — 공개 번호가 될 값이다 (#204).
        assertEquals(1L, insertProblemReturningId())
    }

    @Test
    fun `사람과 게시판과 분류는 남는다`() {
        insertProblem()
        val post = insertPost()
        insertTag()
        val usersBefore = count("users")

        reset()

        // CASCADE 가 여기까지 오면 안 된다.
        assertEquals(
            listOf(usersBefore, 1, 1),
            listOf(count("users"), count("posts"), count("tags")),
            "users·posts·tags 는 남아야 한다",
        )
        assertEquals(post, jdbcClient.sql("SELECT id FROM posts").query(Long::class.java).single())
    }

    @Test
    fun `문제를 가리키는 표는 전부 지우거나 남기거나 둘 중 하나에 적혀 있다`() {
        /*
          **손으로 적는 목록은 반드시 뒤처진다** (#597). 실제로 `problem_allowed_runtimes`
          하나가 빠져 초기화가 외래키 위반으로 죽었고, 그 뒤에 아홉 개가 더 빠져 있었다.

          목록을 DB 에서 자동으로 만들지는 않는다 — 무엇을 **남길지**가 이 기능의 뜻이고
          그 판단은 사람이 한다. 다만 "새 표가 생겼는데 어느 쪽에도 없다" 는 여기서 잡는다.
        */
        val children = jdbcClient.sql(
            """
            SELECT DISTINCT tc.table_name
            FROM information_schema.table_constraints tc
            JOIN information_schema.constraint_column_usage ccu
              ON tc.constraint_name = ccu.constraint_name
            WHERE tc.constraint_type = 'FOREIGN KEY'
              AND ccu.table_name IN ('problems', 'submissions')
              AND tc.table_name NOT IN ('problems', 'submissions')
            """,
        ).query(String::class.java).list()

        val known = DataResetService.RESET_TABLES + DataResetService.KEEP
        val forgotten = children.filterNot { it in known }

        assert(forgotten.isEmpty()) {
            "문제·제출을 가리키는 표가 초기화 목록에도 KEEP 에도 없습니다: $forgotten\n" +
                "지울 것이면 DataResetService.tables 에, 남길 것이면 KEEP 에 이유와 함께 넣으십시오."
        }
    }

    @Test
    fun `확인 문구가 다르면 아무것도 지우지 않는다`() {
        insertProblem()

        mockMvc.perform(
            post("/api/v1/admin/data/reset")
                .header("Authorization", "Bearer $superuserToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"confirmation":"reset"}"""),
        ).andExpect(status().isBadRequest)

        assertEquals(1, count("problems"))
    }

    @Test
    fun `최고 관리자가 아니면 부를 수 없다`() {
        // 어드민이어도 안 된다 (#103). 되돌릴 수 없는 조작은 역할로도 막는다.
        mockMvc.perform(
            post("/api/v1/admin/data/reset")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body()),
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `무엇을 지웠는지 돌려준다`() {
        insertProblem()
        insertSubmission()

        mockMvc.perform(
            post("/api/v1/admin/data/reset")
                .header("Authorization", "Bearer $superuserToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.clearedRows").value(2))
            .andExpect(jsonPath("$.clearedTables").isArray)
    }

    private fun reset() {
        mockMvc.perform(
            post("/api/v1/admin/data/reset")
                .header("Authorization", "Bearer $superuserToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body()),
        ).andExpect(status().isOk)
    }

    private fun body() = """{"confirmation":"문제와 제출을 모두 지웁니다"}"""

    private fun count(table: String): Int =
        jdbcClient.sql("SELECT count(*) FROM $table").query(Int::class.java).single()

    private fun insertProblem() {
        jdbcClient.sql(
            """
            INSERT INTO problems (slug, title, category, difficulty_level, description, published)
            VALUES ('p', '문제', 'ALGORITHM', 1, '설명', true)
            """,
        ).update()
    }

    private fun insertProblemReturningId(): Long =
        jdbcClient.sql(
            """
            INSERT INTO problems (slug, title, category, difficulty_level, description, published)
            VALUES ('after-reset', '초기화 뒤', 'ALGORITHM', 1, '설명', true)
            RETURNING id
            """,
        ).query(Long::class.java).single()

    private fun insertSubmission() {
        val problemId = jdbcClient.sql("SELECT id FROM problems LIMIT 1").query(Long::class.java).single()
        val userId = jdbcClient.sql("SELECT id FROM users LIMIT 1").query(Long::class.java).single()
        jdbcClient.sql(
            """
            INSERT INTO submissions
                (user_id, problem_id, runtime_id, source_code, status, kind, created_at, updated_at)
            VALUES (:userId, :problemId, 'python:3.12', 'print(1)', 'PENDING', 'USER', now(), now())
            """,
        ).param("userId", userId).param("problemId", problemId).update()
    }

    private fun insertPost(): Long {
        val userId = jdbcClient.sql("SELECT id FROM users LIMIT 1").query(Long::class.java).single()
        return jdbcClient.sql(
            """
            INSERT INTO posts (board, title, body, author_id, created_at, updated_at)
            VALUES ('FREE', '남아야 하는 글', '본문', :userId, now(), now())
            RETURNING id
            """,
        ).param("userId", userId).query(Long::class.java).single()
    }

    private fun insertTag() {
        jdbcClient.sql("INSERT INTO tags (slug, name) VALUES ('impl', '구현')").update()
    }
}
