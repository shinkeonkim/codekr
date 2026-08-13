package codekr.api.problem.sql

import codekr.api.auth.security.JwtTokenProvider
import codekr.api.support.IntegrationTestBase
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import codekr.api.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** SQL 문제 등록과 제출 (#60). */
class SqlProblemIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider

    private lateinit var adminToken: String
    private lateinit var userToken: String

    @BeforeEach
    fun setUp() {
        val admin = userRepository.save(
            User("admin@codekr.dev", "x", "관리자", setOf(UserRole.USER, UserRole.SUPERUSER)),
        )
        adminToken = tokenProvider.issueAccessToken(admin)
        val user = userRepository.save(User("solver@codekr.dev", "x", "풀이왕", setOf(UserRole.USER)))
        userToken = tokenProvider.issueAccessToken(user)
    }

    @Test
    fun `테스트케이스 없이도 SQL 문제를 공개할 수 있다`() {
        // SQL 문제의 채점 대상은 테스트케이스가 아니라 정답 쿼리다.
        val id = createSqlProblem()

        mockMvc.perform(get("/api/v1/admin/problems/$id").header("Authorization", "Bearer $adminToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.problemKind").value("JUDGE_SQL"))
            .andExpect(jsonPath("$.sqlSpec.answerSql").value(ANSWER))
            .andExpect(jsonPath("$.sqlSpec.ignoreRowOrder").value(true))
            .andExpect(jsonPath("$.testcases.length()").value(0))
    }

    @Test
    fun `SQL 문제인데 스펙이 없으면 거부한다`() {
        mockMvc.perform(
            post("/api/v1/admin/problems")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("no-spec", withSpec = false)),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `SQL 문제가 아닌데 스펙이 실려 오면 거부한다`() {
        // 섞이면 어느 쪽이 진짜인지 알 수 없다.
        mockMvc.perform(
            post("/api/v1/admin/problems")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("stdio-with-spec", kind = "JUDGE_STDIO")),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `유형을 바꾸면 옛 SQL 스펙이 사라진다`() {
        val id = createSqlProblem()

        // 남겨 두면 유형을 되돌렸을 때 옛 스키마가 되살아나는데,
        // 그것이 지금 지문과 맞는다는 보장이 없다.
        mockMvc.perform(
            put("/api/v1/admin/problems/$id")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(stdioBody("sql-city")),
        ).andExpect(status().isOk)

        mockMvc.perform(get("/api/v1/admin/problems/$id").header("Authorization", "Bearer $adminToken"))
            .andExpect(jsonPath("$.sqlSpec").doesNotExist())
    }

    @Test
    fun `SQL 문제에는 SQL 런타임으로 제출한다`() {
        createSqlProblem()

        mockMvc.perform(
            post("/api/v1/problems/sql-city/submissions")
                .header("Authorization", "Bearer $userToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"runtimeId":"sql:postgres16","sourceCode":"SELECT city FROM members;"}"""),
        ).andExpect(status().isAccepted)

        // 알고리즘 런타임으로는 낼 수 없다 — 고를 수 있지만 채점되지 않는 조합을 만들지 않는다.
        mockMvc.perform(
            post("/api/v1/problems/sql-city/submissions")
                .header("Authorization", "Bearer $userToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"runtimeId":"python:3.12","sourceCode":"print(1)"}"""),
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `SQL 문제 화면에는 SQL 실행 환경만 보인다`() {
        createSqlProblem()

        // 전체를 내리면 화면이 SQL 문제에 파이썬을 권하게 된다.
        mockMvc.perform(get("/api/v1/problems/sql-city"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.runtimes.length()").value(1))
            .andExpect(jsonPath("$.runtimes[0].id").value("sql:postgres16"))
            .andExpect(jsonPath("$.runtimes[0].monacoLanguage").value("sql"))
    }

    @Test
    fun `SQL 문제도 재채점할 수 있다`() {
        /*
          **테스트케이스가 0개인 것이 정상이다** (#60). 그것을 요구하면 스키마를 고친 뒤
          지난 제출을 다시 채점할 방법이 없다 (#495).
        */
        val id = createSqlProblem()
        mockMvc.perform(
            post("/api/v1/problems/sql-city/submissions")
                .header("Authorization", "Bearer $userToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"runtimeId":"sql:postgres16","sourceCode":"SELECT city FROM members;"}"""),
        ).andExpect(status().isAccepted)

        mockMvc.perform(
            post("/api/v1/admin/problems/$id/rejudge")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"reason":"스키마를 고쳤습니다."}"""),
        ).andExpect(status().is2xxSuccessful)
    }

    @Test
    fun `SQL 문제에서 정답 코드 검증은 할 수 없다고 말한다`() {
        // 전에는 "테스트케이스가 없다" 가 나왔다 — 출제자에게 **고칠 수 없는 것**을
        // 고치라고 말하는 셈이었다 (#495).
        val id = createSqlProblem()

        mockMvc.perform(
            post("/api/v1/admin/problems/$id/verify").header("Authorization", "Bearer $adminToken"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(
                jsonPath("$.message").value(org.hamcrest.Matchers.containsString("지원하지 않습니다")),
            )
    }

    private fun createSqlProblem(): Long {
        val response = mockMvc.perform(
            post("/api/v1/admin/problems")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("sql-city")),
        ).andExpect(status().isCreated).andReturn().response.contentAsString

        return Regex("\"id\":(\\d+)").find(response)!!.groupValues[1].toLong()
    }

    private fun body(slug: String, kind: String = "JUDGE_SQL", withSpec: Boolean = true): String {
        // SQL 문제는 어느 DB 인지 정해야 한다 (#454). 유형이 아닌 요청은 비운다 —
        // 그쪽은 SQL 런타임을 적을 수 없다(종류 규칙이 먼저다).
        val databases = if (kind == "JUDGE_SQL") "\"sql:postgres16\"" else ""
        val spec = if (withSpec) {
            """,
              "sqlSpec": {
                "schemaSql": "CREATE TABLE members (id int, city text);",
                "answerSql": "$ANSWER",
                "ignoreRowOrder": true
              }"""
        } else {
            ""
        }
        return """
            {
              "slug": "$slug", "title": "도시별 인원",
              "category": "SQL", "problemKind": "$kind", "difficulty": "SILVER_5",
              "description": "도시별 인원을 세세요.", "published": true,
              "allowedRuntimeIds": [$databases],
              "testcases": [], "templates": []$spec
            }
        """.trimIndent()
    }

    private fun stdioBody(slug: String) = """
        {
          "slug": "$slug", "title": "도시별 인원",
          "category": "SQL", "problemKind": "JUDGE_STDIO", "difficulty": "SILVER_5",
          "description": "도시별 인원을 세세요.", "published": true,
          "testcases": [{"seq": 1, "input": "1", "expectedOutput": "1", "visibility": "PUBLIC"}],
          "templates": []
        }
    """.trimIndent()

    private companion object {
        const val ANSWER = "SELECT city, count(*) FROM members GROUP BY city ORDER BY city;"
    }
}
