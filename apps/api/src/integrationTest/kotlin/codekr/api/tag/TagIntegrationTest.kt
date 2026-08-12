package codekr.api.tag

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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** 문제 태그 (#232). */
class TagIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var jdbcClient: JdbcClient

    private lateinit var adminToken: String
    private lateinit var userToken: String

    @BeforeEach
    fun setUp() {
        adminToken = tokenProvider.issueAccessToken(
            userRepository.save(User("admin@codekr.dev", "x", "관리자", setOf(UserRole.USER, UserRole.SUPERUSER))),
        )
        userToken = tokenProvider.issueAccessToken(
            userRepository.save(User("user@codekr.dev", "x", "풀이왕", setOf(UserRole.USER))),
        )
        insertProblem(1, "two-sum", published = true)
        insertProblem(2, "hidden-one", published = false)
    }

    @Test
    fun `태그를 만들고 문제에 붙인다`() {
        val tagId = createTag("dp", "다이나믹 프로그래밍")

        mockMvc.perform(
            put("/api/v1/admin/problems/1/tags")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"tagIds":[$tagId]}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].slug").value("dp"))

        mockMvc.perform(get("/api/v1/problems/two-sum"))
            .andExpect(jsonPath("$.tags.length()").value(1))
            .andExpect(jsonPath("$.tags[0].name").value("다이나믹 프로그래밍"))
    }

    @Test
    fun `태그를 통째로 바꾼다`() {
        // 하나씩 더하고 빼는 API 였다면 화면과 서버가 서로 다른 상태를 갖는 구간이 생긴다.
        val dp = createTag("dp", "다이나믹 프로그래밍")
        val graph = createTag("graph", "그래프")
        replaceTags(1, dp, graph)

        replaceTags(1, graph)

        mockMvc.perform(get("/api/v1/problems/two-sum"))
            .andExpect(jsonPath("$.tags.length()").value(1))
            .andExpect(jsonPath("$.tags[0].slug").value("graph"))
    }

    @Test
    fun `태그별 문제 수는 공개된 문제만 센다`() {
        // "12문제" 라고 해 놓고 목록에 3개만 나오면 그 수는 거짓말이다.
        val dp = createTag("dp", "다이나믹 프로그래밍")
        replaceTags(1, dp)
        replaceTags(2, dp)

        mockMvc.perform(get("/api/v1/tags"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].slug").value("dp"))
            .andExpect(jsonPath("$[0].problemCount").value(1))
    }

    @Test
    fun `태그 목록은 로그인하지 않아도 볼 수 있다`() {
        createTag("dp", "다이나믹 프로그래밍")

        mockMvc.perform(get("/api/v1/tags")).andExpect(status().isOk)
    }

    @Test
    fun `일반 사용자는 태그를 만들 수 없다`() {
        mockMvc.perform(
            post("/api/v1/admin/tags")
                .header("Authorization", "Bearer $userToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"slug":"dp","name":"다이나믹 프로그래밍"}"""),
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `같은 주소나 이름의 태그는 두 번 만들 수 없다`() {
        // 비슷한 태그가 둘 생기면 필터가 둘로 갈라져 어느 쪽도 온전하지 않다.
        createTag("dp", "다이나믹 프로그래밍")

        postTag("""{"slug":"dp","name":"다른 이름"}""").andExpect(status().isBadRequest)
        postTag("""{"slug":"other","name":"다이나믹 프로그래밍"}""").andExpect(status().isBadRequest)
    }

    @Test
    fun `없는 태그를 붙이려 하면 거부한다`() {
        mockMvc.perform(
            put("/api/v1/admin/problems/1/tags")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"tagIds":[999]}"""),
        ).andExpect(status().isBadRequest)
    }

    private fun createTag(slug: String, name: String): Long {
        val body = postTag("""{"slug":"$slug","name":"$name"}""")
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        return Regex("\"id\":(\\d+)").find(body)!!.groupValues[1].toLong()
    }

    private fun postTag(json: String) = mockMvc.perform(
        post("/api/v1/admin/tags")
            .header("Authorization", "Bearer $adminToken")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json),
    )

    private fun replaceTags(problemId: Long, vararg tagIds: Long) {
        mockMvc.perform(
            put("/api/v1/admin/problems/$problemId/tags")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"tagIds":[${tagIds.joinToString(",")}]}"""),
        ).andExpect(status().isOk)
    }

    private fun insertProblem(id: Long, slug: String, published: Boolean) {
        jdbcClient.sql(
            """
            INSERT INTO problems (id, slug, title, category, difficulty_level, description, published)
            VALUES (:id, :slug, '문제', 'ALGORITHM', 1, '설명', :published)
            """,
        ).param("id", id).param("slug", slug).param("published", published).update()
    }
}
