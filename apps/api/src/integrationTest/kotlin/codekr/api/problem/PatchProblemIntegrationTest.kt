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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 읽고 고치는 문제 (#651).
 *
 * **장치가 전부 이미 있어서 새 표가 없다.** 그래서 여기서 확인할 것은 그 장치들이
 * 이 유형에 **실제로 붙었는가**와, 이 유형만의 규칙 둘이다:
 *
 * 1. **숨긴 시험이 새지 않는다** — 하네스가 공개 상세에 실리면 안 된다
 * 2. **고칠 것이 있어야 한다** — 시작 코드가 없으면 "고치기" 가 아니라 "처음부터 쓰기" 다
 */
class PatchProblemIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider

    private lateinit var adminToken: String

    @BeforeEach
    fun setUp() {
        adminToken = tokenProvider.issueAccessToken(
            userRepository.save(User("admin@codekr.dev", "x", "운영자", setOf(UserRole.ADMIN))),
        )
    }

    /** 숨긴 시험. **어드민에게만 가는 자리**(`harnesses`)에 싣는다. */
    private val hiddenTest =
        "from solution import average\\nassert average([1, 2, 3]) == 2\\nprint('ok')"

    private fun create(
        slug: String = "fix-average",
        files: String = """
            [{"runtimeId":"python:3.13","name":"solution.py",
              "template":"def average(xs):\\n    return sum(xs) / len(xs) - 1\\n","editable":true}]
        """.trimIndent(),
        harnesses: String = """{"python:3.13":"$hiddenTest"}""",
        published: Boolean = true,
    ) = mockMvc.perform(
        post("/api/v1/admin/problems")
            .header("Authorization", "Bearer $adminToken")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"slug":"$slug","title":"평균이 하나 모자랍니다","category":"ALGORITHM",
                 "problemKind":"JUDGE_PATCH","description":"평균을 구하는 코드가 틀렸습니다. 고치세요.",
                 "published":$published,
                 "testcases":[{"seq":1,"input":"","expectedOutput":"ok","visibility":"PUBLIC"}],
                 "files":$files,"harnesses":$harnesses}
                """.trimIndent(),
            ),
    )

    @Test
    fun `시작 코드와 숨긴 시험이 있으면 등록된다`() {
        create().andExpect(status().isCreated)
    }

    /**
     * **숨긴 시험이 새면 문제가 무너진다.**
     *
     * 하네스는 어드민 DTO 에만 자리가 있다 — 공개 상세에 그 자리를 만들지 않은 것이
     * 유일한 방어이고, 그래서 문자열째로 확인한다.
     */
    @Test
    fun `공개 상세에 숨긴 시험이 실리지 않는다`() {
        create().andExpect(status().isCreated)

        val body = mockMvc.perform(get("/api/v1/problems/fix-average"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.problemKind").value("JUDGE_PATCH"))
            // 시작 코드는 보여야 한다 — 그것을 읽는 것이 문제의 절반이다.
            .andExpect(jsonPath("$.runtimes[0].files[0].name").value("solution.py"))
            .andExpect(jsonPath("$.runtimes[0].files[0].editable").value(true))
            .andReturn().response.contentAsString

        assert(!body.contains("assert average")) { "숨긴 시험이 응답에 남아 있습니다" }
    }

    /**
     * **고칠 것이 없으면 고치는 문제가 아니다.**
     *
     * 시작 코드가 없으면 사용자는 빈 파일을 받고, 그것은 "처음부터 쓰기" 다 —
     * 문제가 묻는 것이 조용히 바뀐다.
     */
    @Test
    fun `고칠 수 있는 파일이 없으면 공개할 수 없다`() {
        create(files = "[]").andExpect(status().isBadRequest)
    }

    /** 읽기 전용 파일만 있는 것도 같다 — 고칠 자리가 없다. */
    @Test
    fun `읽기 전용 파일만 있으면 공개할 수 없다`() {
        create(
            files = """[{"runtimeId":"python:3.13","name":"lib.py","template":"x = 1","editable":false}]""",
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `하네스가 없으면 공개할 수 없다`() {
        create(harnesses = "{}").andExpect(status().isBadRequest)
    }

    /** 초안이면 아직 덜 채워도 된다 — 만들다 저장하는 흐름이 막히면 안 된다. */
    @Test
    fun `초안은 덜 채워도 저장된다`() {
        create(slug = "draft-patch", files = "[]", harnesses = "{}", published = false)
            .andExpect(status().isCreated)
    }

    /**
     * **허용 언어는 하네스가 정한다** — 함수 구현(#421)과 같은 규칙이다.
     * 두 곳이 같은 것을 정하면 어긋난다.
     */
    @Test
    fun `허용 언어를 따로 고르면 거부한다`() {
        mockMvc.perform(
            post("/api/v1/admin/problems")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"slug":"both","title":"둘 다 정한 문제","category":"ALGORITHM",
                     "problemKind":"JUDGE_PATCH","description":"설명","published":true,
                     "testcases":[{"seq":1,"input":"","expectedOutput":"ok","visibility":"PUBLIC"}],
                     "files":[{"runtimeId":"python:3.13","name":"solution.py","template":"x","editable":true}],
                     "harnesses":{"python:3.13":"$hiddenTest"},
                     "allowedRuntimeIds":["python:3.13"]}
                    """.trimIndent(),
                ),
        ).andExpect(status().isBadRequest)
    }

    /** 하네스를 쓰는 유형이 아니면 실을 수 없다 — 규칙이 유형에 매여 있어야 한다. */
    @Test
    fun `하네스를 쓰지 않는 유형에 하네스를 실으면 거부한다`() {
        mockMvc.perform(
            post("/api/v1/admin/problems")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"slug":"plain","title":"보통 문제","category":"ALGORITHM",
                     "problemKind":"JUDGE_STDIO","description":"설명","published":false,
                     "harnesses":{"python:3.13":"$hiddenTest"}}
                    """.trimIndent(),
                ),
        ).andExpect(status().isBadRequest)
    }

    /** 이 유형은 정답 코드 검증(#39)을 지원한다 — 고친 코드를 돌려 보는 것이 그 대응물이다. */
    @Test
    fun `정답 코드 검증을 지원한다고 알린다`() {
        create().andExpect(status().isCreated)
        val id = jdbcOfBase.sql("SELECT id FROM problems ORDER BY id DESC LIMIT 1")
            .query(Long::class.java).single()

        mockMvc.perform(
            get("/api/v1/admin/problems/$id").header("Authorization", "Bearer $adminToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.canVerifySolution").value(true))
            .andExpect(jsonPath("$.harnesses['python:3.13']").exists())
    }
}
