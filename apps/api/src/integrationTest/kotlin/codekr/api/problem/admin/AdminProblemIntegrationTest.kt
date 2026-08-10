package codekr.api.problem.admin

import codekr.api.auth.security.JwtTokenProvider
import codekr.api.problem.repository.ProblemRepository
import codekr.api.support.IntegrationTestBase
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import codekr.api.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AdminProblemIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var problemRepository: ProblemRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider

    private lateinit var adminToken: String
    private lateinit var userToken: String

    @BeforeEach
    fun setUp() {
        adminToken = tokenProvider.issueAccessToken(
            userRepository.save(User("admin@codekr.dev", "x", "관리자", setOf(UserRole.ADMIN))),
        )
        userToken = tokenProvider.issueAccessToken(
            userRepository.save(User("member@codekr.dev", "x", "일반유저", setOf(UserRole.USER))),
        )
    }

    @Test
    fun `어드민이 문제를 등록하면 공개 목록에서 조회된다`() {
        createProblem("sum-two", published = true)

        mockMvc.perform(get("/api/v1/problems").param("q", "합"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
    }

    @Test
    fun `일반 사용자는 어드민 API 에 접근할 수 없다`() {
        mockMvc.perform(get("/api/v1/admin/problems").header("Authorization", "Bearer $userToken"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `slug 가 중복되면 409 다`() {
        createProblem("dup-slug", published = true)

        mockMvc.perform(
            post("/api/v1/admin/problems")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("dup-slug", true)),
        ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("SLUG_ALREADY_EXISTS"))
    }

    @Test
    fun `테스트케이스 없이 공개하면 400 이다`() {
        mockMvc.perform(
            post("/api/v1/admin/problems")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"slug":"no-tc","title":"제목","category":"ALGORITHM","difficulty":"BRONZE_5",
                     "description":"설명","published":true,"testcases":[]}
                    """.trimIndent(),
                ),
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("TESTCASE_REQUIRED"))
    }

    @Test
    fun `지원하지 않는 실행 환경의 초기 코드는 거부한다`() {
        mockMvc.perform(
            post("/api/v1/admin/problems")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"slug":"bad-runtime","title":"제목","category":"ALGORITHM","difficulty":"SILVER_3",
                     "description":"설명","published":false,
                     "templates":[{"runtimeId":"ruby:3.3","sourceCode":"puts 1"}]}
                    """.trimIndent(),
                ),
        ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("RUNTIME_NOT_FOUND"))
    }

    @Test
    fun `수정하면 테스트케이스와 초기 코드가 통째로 교체된다`() {
        val id = createProblem("editable", published = false)

        mockMvc.perform(
            put("/api/v1/admin/problems/$id")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("editable", true, title = "수정된 두 수의 합", difficulty = "PLATINUM_2")),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.title").value("수정된 두 수의 합"))
            .andExpect(jsonPath("$.difficulty").value("PLATINUM_2"))
            .andExpect(jsonPath("$.difficultyLabel").value("플래티넘 2"))
            .andExpect(jsonPath("$.testcases.length()").value(2))
            .andExpect(jsonPath("$.templates.length()").value(1))
    }

    @Test
    fun `삭제는 소프트 삭제이며 목록에서 사라진다`() {
        val id = createProblem("removable", published = true)

        mockMvc.perform(delete("/api/v1/admin/problems/$id").header("Authorization", "Bearer $adminToken"))
            .andExpect(status().isNoContent)

        // 행은 남아 있지만 살아 있는 문제로는 조회되지 않는다.
        assertNotNull(problemRepository.findById(id).orElse(null))
        assertNull(problemRepository.findByIdAndDeletedAtIsNull(id))

        mockMvc.perform(get("/api/v1/admin/problems").header("Authorization", "Bearer $adminToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(0))
    }

    @Test
    fun `삭제한 slug 를 다시 사용할 수 있다`() {
        val id = createProblem("reusable", published = true)
        mockMvc.perform(delete("/api/v1/admin/problems/$id").header("Authorization", "Bearer $adminToken"))
            .andExpect(status().isNoContent)

        createProblem("reusable", published = true)
    }

    private fun createProblem(slug: String, published: Boolean): Long {
        val response = mockMvc.perform(
            post("/api/v1/admin/problems")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(slug, published)),
        ).andExpect(status().isCreated).andReturn().response.contentAsString

        return Regex("\"id\":(\\d+)").find(response)!!.groupValues[1].toLong()
    }

    private fun body(
        slug: String,
        published: Boolean,
        title: String = "두 수의 합",
        difficulty: String = "BRONZE_5",
    ) = """
        {
          "slug": "$slug", "title": "$title",
          "category": "ALGORITHM", "difficulty": "$difficulty",
          "description": "두 정수를 더한다.", "timeLimitMs": 2000, "memoryLimitMb": 256,
          "published": $published,
          "testcases": [
            {"seq": 1, "input": "1 2\n", "expectedOutput": "3\n", "visibility": "PUBLIC"},
            {"seq": 2, "input": "10 20\n", "expectedOutput": "30\n", "visibility": "HIDDEN"}
          ],
          "templates": [
            {"runtimeId": "python:3.12", "sourceCode": "# 여기에 작성하세요\n"}
          ]
        }
    """.trimIndent()
}
