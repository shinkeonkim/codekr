package codekr.api.problem

import codekr.api.auth.security.JwtTokenProvider
import codekr.api.support.IntegrationTestBase
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import codekr.api.user.repository.UserRepository
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * SQL 문제를 묶음으로 올린다 (#561).
 *
 * **스키마는 별도 파일이다.** 다섯 문제가 같은 스키마를 공유하고, 여러 줄 SQL 은 JSON
 * 문자열 안에서 읽을 수 없기 때문이다 (#313). 그래서 묶음도 파일로 받는다 —
 * 테스트케이스를 파일로 뺀 것과 같은 이유다.
 */
class ProblemImportSqlIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var jdbcClient: JdbcClient

    private lateinit var adminToken: String

    @BeforeEach
    fun setUp() {
        val admin = userRepository.save(
            User("admin@codekr.dev", "x", "관리자", setOf(UserRole.USER, UserRole.SUPERUSER)),
        )
        adminToken = tokenProvider.issueAccessToken(admin)
    }

    @Test
    fun `시드 SQL 문제를 묶어 올리면 스키마까지 들어간다`() {
        // **합격 기준이다.** 손으로 만든 것이 아니라 저장소의 진짜 시드 파일과
        // 그것이 가리키는 진짜 스키마를 zip 으로 묶어 올린다.
        val id = post("/imports", seedBundle("08-sql-seoul-members.json"))
            .andExpect(status().isCreated)
            .andReturn().response.contentAsString
            .let { Regex("\"id\":(\\d+)").find(it)!!.groupValues[1].toLong() }

        val schema = jdbcClient.sql("SELECT schema_sql FROM problem_sql_specs WHERE problem_id = :id")
            .param("id", id).query(String::class.java).single()
        assert(schema.contains("CREATE TABLE")) { "스키마가 안 들어갔다: ${schema.take(80)}" }
        assert(schema.contains("members")) { "스키마 내용이 다르다: ${schema.take(80)}" }
    }

    @Test
    fun `SQL 문제는 테스트케이스가 없어도 경고 대상이 아니다`() {
        // SQL 은 정답 쿼리로 채점한다 — 0개가 정상이다.
        post("/imports/preview", seedBundle("08-sql-seoul-members.json"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.problemKind").value("JUDGE_SQL"))
            .andExpect(jsonPath("$.testcaseCount").value(0))
            .andExpect(jsonPath("$.needsTestcases").value(false))
            .andExpect(jsonPath("$.violations.length()").value(0))
    }

    @Test
    fun `스키마 파일이 빠지면 거절한다`() {
        // 조용히 빈 스키마로 만들면 채점이 통째로 어긋난다.
        val onlyMeta = zip(listOf("problem.json" to seedJson("08-sql-seoul-members.json")))

        post("/imports", onlyMeta)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(containsString("sql/library.sql")))
    }

    @Test
    fun `맨 JSON 으로는 스키마 파일을 읽을 수 없다고 알린다`() {
        post("/imports", seedJson("08-sql-seoul-members.json").toByteArray())
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(containsString("맨 JSON")))
    }

    @Test
    fun `스키마를 두 곳에 적으면 거절한다`() {
        // 손으로 적은 것 둘이 어긋나면 어느 쪽이 뜻인지 우리가 정할 일이 아니다.
        val both = seedJson("08-sql-seoul-members.json")
            .replace("\"answerSql\"", "\"schemaSql\": \"CREATE TABLE x(a int);\", \"answerSql\"")

        post("/imports", zip(listOf("problem.json" to both, "sql/library.sql" to "CREATE TABLE y(b int);")))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(containsString("둘 다 있습니다")))
    }

    @Test
    fun `아무도 안 가리키는 파일은 여전히 거절한다`() {
        // 모아 두는 것과 받아들이는 것은 다르다. 버리면 넣은 사람은 들어간 줄 안다.
        post("/imports", zip(listOf("problem.json" to seedJson("04-a-plus-b.json"), "메모.txt" to "여기 뭘 적었더라")))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(containsString("메모.txt")))
    }

    private fun post(path: String, body: ByteArray) = mockMvc.perform(
        multipart("/api/v1/admin/problems$path")
            .file(MockMultipartFile("file", "problem.zip", "application/zip", body))
            .header("Authorization", "Bearer $adminToken"),
    )

    /** 시드 JSON 과 그것이 가리키는 스키마 파일을 함께 묶는다. */
    private fun seedBundle(name: String): ByteArray {
        val meta = seedJson(name)
        val schemaPath = Regex("\"sqlSchemaFile\"\\s*:\\s*\"([^\"]+)\"").find(meta)!!.groupValues[1]
        return zip(listOf("problem.json" to meta, schemaPath to seedText(schemaPath)))
    }

    private fun seedJson(name: String) = seedText(name)

    /** 저장소의 진짜 시드 파일. 작업 디렉터리가 어디든 찾을 수 있게 위로 올라가며 본다. */
    private fun seedText(relative: String): String {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "scripts/seed-problems/$relative")
            if (candidate.isFile) return candidate.readText()
            dir = dir.parentFile
        }
        throw IllegalStateException("시드 파일을 찾지 못했습니다: $relative")
    }

    private fun zip(entries: List<Pair<String, String>>): ByteArray {
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { out ->
            entries.forEach { (name, body) ->
                out.putNextEntry(ZipEntry(name))
                out.write(body.toByteArray())
                out.closeEntry()
            }
        }
        return buffer.toByteArray()
    }
}
