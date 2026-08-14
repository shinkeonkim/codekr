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
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 맨 JSON 수용과 미리보기 (#537).
 *
 * #479 는 zip 만 받았는데 **우리가 가진 파일은 대부분 zip 이 아니다** —
 * `scripts/seed-problems` 의 18개도, 스킬(#480~#482)이 내놓는 것도 맨 JSON 이다.
 */
class ProblemImportJsonIntegrationTest : IntegrationTestBase() {

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
    fun `시드 파일을 그대로 올려서 문제가 만들어진다`() {
        // **이것이 이 이슈의 합격 기준이다.** 손으로 만든 JSON 이 아니라 저장소에 있는
        // 진짜 시드 파일을 올린다 — 형식이 갈라지면 여기서 깨진다.
        val id = post("/imports", seedFile("04-a-plus-b.json"))
            .andExpect(status().isCreated)
            .andReturn().response.contentAsString
            .let { Regex("\"id\":(\\d+)").find(it)!!.groupValues[1] }

        mockMvc.perform(get("/api/v1/admin/problems/$id").header("Authorization", "Bearer $adminToken"))
            .andExpect(jsonPath("$.slug").value("a-plus-b"))
            .andExpect(jsonPath("$.testcases.length()").value(6))
    }

    @Test
    fun `맨 JSON 도 언제나 초안으로 들어온다`() {
        // 04-a-plus-b.json 에는 `published: true` 가 적혀 있다. 그래도 덮는다.
        post("/imports", seedFile("04-a-plus-b.json")).andExpect(status().isCreated)
        mockMvc.perform(get("/api/v1/problems/a-plus-b")).andExpect(status().isNotFound)
    }

    @Test
    fun `미리보기는 아무것도 만들지 않는다`() {
        post("/imports/preview", seedFile("04-a-plus-b.json"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.source").value("JSON"))
            .andExpect(jsonPath("$.slug").value("a-plus-b"))
            .andExpect(jsonPath("$.testcaseCount").value(6))
            .andExpect(jsonPath("$.testcaseSource").value("INLINE"))
            // 묶음에 적힌 값 그대로 보인다 — "적혀 있지만 초안으로 들어간다" 를 말해야 한다.
            .andExpect(jsonPath("$.publishedInBundle").value(true))
            .andExpect(jsonPath("$.violations.length()").value(0))

        // 미리보기만 했으므로 어디에도 없다.
        mockMvc.perform(
            get("/api/v1/admin/problems?q=a-plus-b").header("Authorization", "Bearer $adminToken"),
        ).andExpect(jsonPath("$.content.length()").value(0))
    }

    @Test
    fun `미리보기는 검증 위반을 모아서 돌려준다`() {
        // 첫 번째에서 멈추면 고치고 다시 올리기를 반복하게 된다.
        post("/imports/preview", json(meta().replace("\"title\": \"두 수의 합\"", "\"title\": \"\"")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.violations.length()").value(1))
            .andExpect(jsonPath("$.violations[0]").value(containsString("title")))
    }

    @Test
    fun `미리보기를 통과하지 못한 것은 저장도 통과하지 못한다`() {
        val broken = json(meta().replace("\"title\": \"두 수의 합\"", "\"title\": \"\""))
        post("/imports/preview", broken).andExpect(jsonPath("$.violations.length()").value(1))
        post("/imports", broken).andExpect(status().isBadRequest)
    }

    @Test
    fun `이름이 아니라 매직 바이트로 가른다`() {
        // `.json` 이라 적힌 zip 도 zip 으로 읽는다. 이름과 Content-Type 은 올리는 쪽이 정한다.
        val zip = zip(listOf("problem.json" to meta(), "testcases/1.in" to "1 2\n", "testcases/1.out" to "3\n"))
        mockMvc.perform(
            multipart("/api/v1/admin/problems/imports/preview")
                .file(MockMultipartFile("file", "problem.json", "application/json", zip))
                .header("Authorization", "Bearer $adminToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.source").value("ZIP"))
            .andExpect(jsonPath("$.testcaseSource").value("FILES"))
    }

    @Test
    fun `모르는 키를 조용히 버리지 않는다`() {
        // 버리면 출제자는 자기가 적은 것이 들어갔다고 믿는다. zip 의 "모르는 파일" 과 같은 규칙이다.
        post("/imports", json(meta().replace("\"templates\": []", "\"templates\": [], \"난수\": 1")))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(containsString("난수")))
    }

    @Test
    fun `SQL 시드는 맨 JSON 으로 올릴 수 없다`() {
        /*
          SQL 시드 일곱 개는 `sqlSchemaFile` 로 스키마를 **별도 파일**에 둔다 (#313).
          맨 JSON 에는 그 파일이 딸려 오지 않으므로 여전히 올릴 수 없다.

          다만 #561 이후로는 **왜 안 되는지**를 말한다 — zip 으로 묶으라는 뜻이
          메시지에 담겨야 사람이 다음 수를 안다. 묶어서 올리는 경로는
          ProblemImportSqlIntegrationTest 가 확인한다.
        */
        post("/imports", seedFile("08-sql-seoul-members.json"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(containsString("zip")))
    }

    private fun post(path: String, body: ByteArray) = mockMvc.perform(
        multipart("/api/v1/admin/problems$path")
            .file(MockMultipartFile("file", "problem.json", "application/json", body))
            .header("Authorization", "Bearer $adminToken"),
    )

    private fun json(body: String) = body.toByteArray()

    /** 저장소의 진짜 시드 파일. 작업 디렉터리가 어디든 찾을 수 있게 위로 올라가며 본다. */
    private fun seedFile(name: String): ByteArray {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "scripts/seed-problems/$name")
            if (candidate.isFile) return candidate.readBytes()
            dir = dir.parentFile
        }
        throw IllegalStateException("시드 파일을 찾지 못했습니다: $name")
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

    private fun meta() = """
        {
          "slug": "json-plain",
          "title": "두 수의 합",
          "category": "ALGORITHM",
          "difficulty": "BRONZE_5",
          "description": "두 수를 더하세요.",
          "timeLimitMs": 2000,
          "memoryLimitMb": 256,
          "published": false,
          "testcases": [{"seq": 1, "input": "1 2\n", "expectedOutput": "3\n"}],
          "templates": []
        }
    """.trimIndent()
}
