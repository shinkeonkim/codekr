package codekr.api.problem.admin

import codekr.api.auth.security.JwtTokenProvider
import codekr.api.support.IntegrationTestBase
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import codekr.api.user.repository.UserRepository
import org.hamcrest.Matchers.everyItem
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 어드민 목록의 거르개 (#626).
 *
 * **`published` 만 새로 받는다.** `q`·`category`·`tier`·`sort` 는 이미 받고 있었는데
 * 화면이 아무것도 보내지 않았다 — 그래서 여기서 함께 확인한다. **받는 것으로 알고 있던
 * 것이 실제로 걸리는지**를 보지 않으면, 화면을 붙이고 나서야 안 걸리는 것을 알게 된다.
 */
class AdminProblemSearchIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider

    private lateinit var adminToken: String

    @BeforeEach
    fun setUp() {
        adminToken = tokenProvider.issueAccessToken(
            userRepository.save(User("admin@codekr.dev", "x", "관리자", setOf(UserRole.ADMIN))),
        )
        create("published-one", published = true, title = "공개된 문제")
        create("draft-one", published = false, title = "초안 하나")
        create("draft-two", published = false, title = "초안 둘", category = "SQL")
    }

    @Test
    fun `공개 여부를 주지 않으면 초안까지 전부 온다`() {
        // 어드민 목록의 기본이다. 여기서 초안이 빠지면 방금 올린 묶음이 보이지 않는다.
        search().andExpect(jsonPath("$.totalElements").value(3))
    }

    @Test
    fun `미공개만 고를 수 있다`() {
        search("published" to "false")
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content[*].published").value(everyItem(`is`(false))))
    }

    @Test
    fun `공개만 고를 수 있다`() {
        search("published" to "true")
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].slug").value("published-one"))
    }

    @Test
    fun `검색어는 초안도 찾는다`() {
        // 사용자 검색은 공개된 것만 본다. 어드민이 초안을 못 찾으면 검색이 반쪽이다.
        search("q" to "초안").andExpect(jsonPath("$.totalElements").value(2))
    }

    @Test
    fun `공개 여부와 분야를 함께 걸면 둘 다 걸린다`() {
        search("published" to "false", "category" to "SQL")
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].slug").value("draft-two"))
    }

    private fun search(vararg params: Pair<String, String>) =
        mockMvc.perform(
            get("/api/v1/admin/problems")
                .header("Authorization", "Bearer $adminToken")
                .apply { params.forEach { (key, value) -> param(key, value) } },
        ).andExpect(status().isOk)

    private fun create(slug: String, published: Boolean, title: String, category: String = "ALGORITHM") {
        mockMvc.perform(
            post("/api/v1/admin/problems")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "slug": "$slug", "title": "$title",
                      "category": "$category", "difficulty": "BRONZE_5",
                      "problemKind": "JUDGE_STDIO",
                      "description": "두 정수를 더한다.", "timeLimitMs": 2000, "memoryLimitMb": 256,
                      "published": $published,
                      "testcases": [
                        {"seq": 1, "input": "1 2\n", "expectedOutput": "3\n", "visibility": "PUBLIC"}
                      ],
                      "templates": []
                    }
                    """.trimIndent(),
                ),
        ).andExpect(status().isCreated)
    }
}
