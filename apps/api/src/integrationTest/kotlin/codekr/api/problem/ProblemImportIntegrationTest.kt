package codekr.api.problem

import codekr.api.auth.security.JwtTokenProvider
import codekr.api.support.IntegrationTestBase
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import codekr.api.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 문제 데이터 업로드 (#479).
 *
 * **테스트케이스가 백 개를 넘으면 폼으로는 못 만든다.** 그리고 압축을 푸는 일은 안전한
 * 작업이 아니다 — 압축 폭탄과 경로 탈출이 있다.
 */
class ProblemImportIntegrationTest : IntegrationTestBase() {

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
    fun `테스트케이스가 많은 문제를 묶음으로 만든다`() {
        val entries = mutableListOf(ProblemArchiveEntry("problem.json", meta("bulk-sum")))
        repeat(300) { index ->
            val seq = index + 1
            entries += ProblemArchiveEntry("testcases/$seq.in", "$seq $seq\n")
            entries += ProblemArchiveEntry("testcases/$seq.out", "${seq * 2}\n")
        }

        val id = upload(entries).andExpect(status().isCreated)
            .andReturn().response.contentAsString
            .let { Regex("\"id\":(\\d+)").find(it)!!.groupValues[1] }

        mockMvc.perform(get("/api/v1/admin/problems/$id").header("Authorization", "Bearer $adminToken"))
            .andExpect(jsonPath("$.testcases.length()").value(300))
            .andExpect(jsonPath("$.testcases[0].input").value("1 1\n"))
    }

    @Test
    fun `묶음으로 올린 문제는 언제나 초안이다`() {
        /*
          올린 것이 바로 공개되면 잘못 만든 묶음이 그대로 사람들 앞에 놓인다.
          **묶음이 무엇이라 적었든** 덮는다 — 아래 메타에는 `published: true` 가 있다.
        */
        upload(
            listOf(
                ProblemArchiveEntry("problem.json", meta("draft-one", published = true)),
                ProblemArchiveEntry("testcases/1.in", "1 2\n"),
                ProblemArchiveEntry("testcases/1.out", "3\n"),
            ),
        ).andExpect(status().isCreated)

        // 공개되지 않았으므로 푸는 화면에서는 없는 문제다.
        mockMvc.perform(get("/api/v1/problems/draft-one")).andExpect(status().isNotFound)
    }

    @Test
    fun `경로 탈출을 막는다`() {
        // `../../etc/passwd` 를 담은 zip 이 실제로 있다.
        upload(
            listOf(
                ProblemArchiveEntry("problem.json", meta("escape")),
                ProblemArchiveEntry("../../etc/passwd", "x"),
            ),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("쓸 수 없는 경로")))
    }

    @Test
    fun `짝이 없는 테스트케이스를 막는다`() {
        // 입력만 있으면 정답이 없고, 그것은 테스트케이스가 아니다.
        upload(
            listOf(
                ProblemArchiveEntry("problem.json", meta("half")),
                ProblemArchiveEntry("testcases/1.in", "1 2\n"),
            ),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("짝이 맞지 않는")))
    }

    @Test
    fun `모르는 파일을 조용히 버리지 않는다`() {
        // 버리면 출제자는 자기가 넣은 것이 들어갔다고 믿는다.
        upload(
            listOf(
                ProblemArchiveEntry("problem.json", meta("stray")),
                ProblemArchiveEntry("testcases/1.in", "1 2\n"),
                ProblemArchiveEntry("testcases/1.out", "3\n"),
                ProblemArchiveEntry("메모.txt", "여기 뭘 적었더라"),
            ),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("모르는 파일")))
    }

    @Test
    fun `problem_json 이 없으면 거절한다`() {
        upload(listOf(ProblemArchiveEntry("testcases/1.in", "1\n")))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("problem.json")))
    }

    @Test
    fun `폼과 같은 규칙을 지난다`() {
        // 여기서만 통과하는 길을 두면 그 길로 들어온 문제가 화면에서 고쳐지지 않는다.
        upload(
            listOf(
                ProblemArchiveEntry("problem.json", meta("no-title").replace("\"title\": \"두 수의 합\",", "\"title\": \"\",")),
                ProblemArchiveEntry("testcases/1.in", "1 2\n"),
                ProblemArchiveEntry("testcases/1.out", "3\n"),
            ),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `폴더째 압축한 묶음도 읽는다`() {
        /*
          `zip -r bundle.zip 내-문제` 는 이름을 `내-문제/problem.json` 으로 만든다 —
          **폴더를 압축하는 가장 자연스러운 방법**이고, 그러면 루트에 problem.json 이
          없어 통째로 거절당했다 (#594).

          접두사는 내용이 아니라 **포장**이라 벗겨도 무엇이 들어왔는지는 그대로다.
        */
        upload(
            listOf(
                ProblemArchiveEntry("내-문제/problem.json", meta("wrapped")),
                ProblemArchiveEntry("내-문제/testcases/1.in", "1 2\n"),
                ProblemArchiveEntry("내-문제/testcases/1.out", "3\n"),
            ),
        ).andExpect(status().isCreated)
    }

    @Test
    fun `맨 위 폴더가 둘이면 벗기지 않는다`() {
        // 무엇이 뜻인지 알 수 없다. 짐작하면 엉뚱한 것을 문제로 만든다.
        upload(
            listOf(
                ProblemArchiveEntry("가/problem.json", meta("two-roots")),
                ProblemArchiveEntry("나/problem.json", meta("two-roots")),
            ),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("problem.json")))
    }

    @Test
    fun `macOS 부스러기는 무시한다`() {
        /*
          macOS 는 폴더를 열기만 해도 `.DS_Store` 를 만들고, Finder 로 압축하면
          `__MACOSX/`·`._*` 가 함께 들어간다. **출제자가 넣은 적이 없는 파일**이라
          "모르는 파일을 버리지 않는다" 는 규칙이 지키려는 것이 여기에는 없다.
        */
        upload(
            listOf(
                ProblemArchiveEntry("problem.json", meta("mac-junk")),
                ProblemArchiveEntry(".DS_Store", "\u0000\u0000"),
                ProblemArchiveEntry("__MACOSX/._problem.json", "x"),
                ProblemArchiveEntry("testcases/.DS_Store", "x"),
                ProblemArchiveEntry("testcases/1.in", "1 2\n"),
                ProblemArchiveEntry("testcases/1.out", "3\n"),
            ),
        ).andExpect(status().isCreated)
    }

    @Test
    fun `부스러기가 아닌 모르는 파일은 여전히 거절한다`() {
        // 목록을 좁게 둔다 — 정확히 아는 이름만 무시하고 나머지는 그대로 거절한다.
        upload(
            listOf(
                ProblemArchiveEntry("내-문제/problem.json", meta("still-strict")),
                ProblemArchiveEntry("내-문제/메모.txt", "여기 뭘 적었더라"),
            ),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("모르는 파일")))
    }

    private fun upload(entries: List<ProblemArchiveEntry>) = mockMvc.perform(
        multipart("/api/v1/admin/problems/imports")
            .file(MockMultipartFile("file", "problem.zip", "application/zip", zip(entries)))
            .header("Authorization", "Bearer $adminToken"),
    )

    private fun zip(entries: List<ProblemArchiveEntry>): ByteArray {
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { out ->
            entries.forEach { entry ->
                out.putNextEntry(ZipEntry(entry.name))
                out.write(entry.body.toByteArray())
                out.closeEntry()
            }
        }
        return buffer.toByteArray()
    }

    private fun meta(slug: String, published: Boolean = false) = """
        {
          "slug": "$slug",
          "title": "두 수의 합",
          "category": "ALGORITHM",
          "difficulty": "BRONZE_5",
          "description": "두 수를 더하세요.",
          "timeLimitMs": 2000,
          "memoryLimitMb": 256,
          "published": $published,
          "testcases": [],
          "templates": []
        }
    """.trimIndent()
}

private data class ProblemArchiveEntry(val name: String, val body: String)
