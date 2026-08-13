package codekr.api.problem

import codekr.api.auth.security.JwtTokenProvider
import codekr.api.support.IntegrationTestBase
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import codekr.api.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Range
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 스페셜 저지 (#452).
 *
 * **채점 코드는 사용자에게 절대 내려가지 않는다** — 정답의 일부나 판정 방식이 들어간다.
 * 그리고 **채점 코드 없이 `CHECKER` 를 고르면 아무도 못 푸는 문제가 된다.**
 */
class SpecialJudgeIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var redisTemplate: StringRedisTemplate

    private var setterToken: String = ""
    private var userToken: String = ""

    @BeforeEach
    fun setUp() {
        val setter = userRepository.save(
            User("setter@codekr.dev", "x", "출제자", setOf(UserRole.USER, UserRole.PROBLEM_SETTER)),
        )
        setterToken = tokenProvider.issueAccessToken(setter)
        val solver = userRepository.save(User("solver@codekr.dev", "x", "푸는사람", setOf(UserRole.USER)))
        userToken = tokenProvider.issueAccessToken(solver)
    }

    @Test
    fun `채점 코드는 문제 상세에 새지 않는다`() {
        create(slug = "any-order", checker = """import sys; SECRET = "정답의 일부"""")

        val body = mockMvc.perform(get("/api/v1/problems/any-order"))
            .andExpect(status().isOk)
            .andReturn().response.getContentAsString(Charsets.UTF_8)

        assert(!body.contains("SECRET")) { "채점 코드가 샜다: $body" }
        assert(!body.contains("checker")) { "채점 코드 자리가 응답에 있다: $body" }
    }

    @Test
    fun `제출하면 채점 코드가 채점 작업에 실린다`() {
        // **채점기가 DB 를 읽지 않는다** (ADR-0004). 실리지 않으면 판정할 방법이 없다.
        create(slug = "carried-checker", checker = "CHECKER SOURCE HERE")

        mockMvc.perform(
            post("/api/v1/problems/carried-checker/submissions")
                .header("Authorization", "Bearer $userToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"runtimeId":"python:3.13","sourceCode":"print(3)","visibility":"PRIVATE"}"""),
        ).andExpect(status().isAccepted)

        val queued = redisTemplate.opsForStream<String, String>()
            .range("codekr:judge:normal", Range.unbounded())
            .orEmpty()
            .joinToString(" ") { it.value.values.joinToString(" ") }

        assert(queued.contains("CHECKER SOURCE HERE")) { "채점 코드가 작업에 없다: $queued" }
    }

    @Test
    fun `채점 코드 없이 채점 코드 판정을 고를 수 없다`() {
        mockMvc.perform(
            post("/api/v1/admin/problems")
                .header("Authorization", "Bearer $setterToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(slug = "no-checker", checker = null)),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("채점 코드")))
    }

    @Test
    fun `정확 일치 문제에는 채점 코드를 실을 수 없다`() {
        // 유형별 자료는 그 유형에만 실린다 (#60 이 SQL 에서 정한 규칙과 같다).
        mockMvc.perform(
            post("/api/v1/admin/problems")
                .header("Authorization", "Bearer $setterToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    body(slug = "exact-with-checker", checker = "x")
                        .replace("\"outputComparison\": \"CHECKER\"", "\"outputComparison\": \"EXACT\""),
                ),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `어드민 상세에는 채점 코드가 온다`() {
        // 고치려면 지금 무엇이 들어 있는지 보여야 한다. **어드민에게만** 간다.
        val id = create(slug = "editable-checker", checker = "CHECKER BODY")

        mockMvc.perform(
            get("/api/v1/admin/problems/$id").header("Authorization", "Bearer $setterToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.checkerSource").value("CHECKER BODY"))
    }

    private fun create(slug: String, checker: String?): Long {
        val response = mockMvc.perform(
            post("/api/v1/admin/problems")
                .header("Authorization", "Bearer $setterToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(slug, checker)),
        ).andExpect(status().isCreated).andReturn().response.getContentAsString(Charsets.UTF_8)
        return Regex("\"id\":(\\d+)").find(response)!!.groupValues[1].toLong()
    }

    private fun body(slug: String, checker: String?): String =
        """
        {
          "slug": "$slug",
          "title": "아무 순서로나 출력하기",
          "category": "ALGORITHM",
          "description": "조건을 만족하는 아무 배치나 출력하세요.",
          "inputDescription": "정수 N",
          "outputDescription": "조건을 만족하는 배치 아무거나",
          "timeLimitMs": 2000,
          "memoryLimitMb": 256,
          "outputComparison": "CHECKER",
          "published": true,
          ${if (checker == null) "" else "\"checkerSource\": ${quote(checker)},"}
          "testcases": [
            {"seq": 1, "input": "3\n", "expectedOutput": "", "visibility": "PUBLIC"}
          ]
        }
        """.trimIndent()

    private fun quote(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
