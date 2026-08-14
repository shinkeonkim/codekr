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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * SQL 문제는 어느 DB 인지 정한다 (#454).
 *
 * SQL 런타임이 둘이 된 순간, **"비워 두면 전부 허용"(#419)의 뜻이 SQL 문제에서
 * 달라졌다** — PostgreSQL 문법으로 쓴 스키마가 MySQL 제출에서도 돌게 된다.
 * 그러면 출제자의 스키마가 먼저 깨져, 제출자는 자기 잘못이 아닌 오류를 받는다.
 */
class SqlDatabaseChoiceIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider

    private lateinit var adminToken: String

    @BeforeEach
    fun setUp() {
        val admin = userRepository.save(
            User("admin@codekr.dev", "x", "관리자", setOf(UserRole.USER, UserRole.SUPERUSER)),
        )
        adminToken = tokenProvider.issueAccessToken(admin)
    }

    @Test
    fun `데이터베이스를 고르지 않은 SQL 문제는 거부한다`() {
        create(slug = "no-db", databases = emptyList())
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("하나만")))
    }

    @Test
    fun `두 데이터베이스를 함께 고를 수 없다`() {
        // 스키마도 정답도 지문도 갈라진다. 같은 질문을 두 DB 로 내려면 문제를 둘 만든다.
        create(slug = "two-db", databases = listOf("sql:postgres16", "sql:mariadb11"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `MariaDB 문제를 낼 수 있다`() {
        create(slug = "mariadb-one", databases = listOf("sql:mariadb11"), timeLimitMs = 15000)
            .andExpect(status().isCreated)

        mockMvc.perform(get("/api/v1/problems/mariadb-one"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.runtimes.length()").value(1))
            .andExpect(jsonPath("$.runtimes[0].id").value("sql:mariadb11"))
    }

    @Test
    fun `기동 시간보다 빠듯한 제한은 거부한다`() {
        /*
          **기동도 문제의 시간 제한 안에서 흐른다** — 제한은 컨테이너 전체에 걸린다.
          MariaDB 는 뜨는 데만 3초 넘게 쓰므로 2초 제한을 준 문제는 어떤 쿼리를 내도
          시간 초과가 되고, 출제자는 그 이유를 짐작할 방법이 없다.
        */
        create(slug = "too-tight", databases = listOf("sql:mariadb11"), timeLimitMs = 2000)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("뜨는 데만")))
    }

    @Test
    fun `PostgreSQL 문제는 지금 제한 그대로 낼 수 있다`() {
        // 기존 문제가 그대로 돌아야 한다 — 0.5초짜리 기동에 5초 제한이면 넉넉하다.
        create(slug = "pg-one", databases = listOf("sql:postgres16"), timeLimitMs = 5000)
            .andExpect(status().isCreated)
    }

    private fun create(
        slug: String,
        databases: List<String>,
        timeLimitMs: Int = 5000,
    ) = mockMvc.perform(
        post("/api/v1/admin/problems")
            .header("Authorization", "Bearer $adminToken")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {
                  "slug": "$slug", "title": "도시별 인원",
                  "category": "SQL", "problemKind": "JUDGE_SQL", "difficulty": "SILVER_5",
                  "description": "도시별 인원을 세세요.", "published": true,
                  "timeLimitMs": $timeLimitMs, "memoryLimitMb": 512,
                  "allowedRuntimeIds": [${databases.joinToString { "\"$it\"" }}],
                  "testcases": [], "templates": [],
                  "sqlSpec": {
                    "schemaSql": "CREATE TABLE members (id int, city varchar(20));",
                    "answerSql": "SELECT city FROM members;",
                    "ignoreRowOrder": true
                  }
                }
                """.trimIndent(),
            ),
    )
}
