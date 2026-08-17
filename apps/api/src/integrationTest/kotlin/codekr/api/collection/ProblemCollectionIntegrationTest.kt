package codekr.api.collection

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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertNotNull

/** 문제집 (#87). */
class ProblemCollectionIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var jdbcClient: JdbcClient

    private var ownerId: Long = 0
    private lateinit var ownerToken: String
    private lateinit var otherToken: String

    @BeforeEach
    fun setUp() {
        val owner = userRepository.save(User("owner@codekr.dev", "x", "주인", setOf(UserRole.USER)))
        ownerId = owner.id
        ownerToken = tokenProvider.issueAccessToken(owner)
        otherToken = tokenProvider.issueAccessToken(
            userRepository.save(User("other@codekr.dev", "x", "남", setOf(UserRole.USER))),
        )

        jdbcClient.sql(
            """
            INSERT INTO problems (id, slug, title, category, difficulty_level, description, published)
            VALUES (1, 'a', 'A 문제', 'ALGORITHM', 1, '설명', true),
                   (2, 'b', 'B 문제', 'ALGORITHM', 6, '설명', true),
                   (3, 'c', 'C 문제', 'ALGORITHM', 11, '설명', true)
            """,
        ).update()
    }

    @Test
    fun `회원 누구나 문제집을 만든다`() {
        // 어드민 전용이 아니다 — 자기 복습 목록을 만드는 것이 더 흔하다.
        create(ownerToken, problems = listOf(1, 2))

        mockMvc.perform(get("/api/v1/collections/me").header("Authorization", "Bearer $ownerToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].problemCount").value(2))
    }

    /**
     * 내 목록도 페이지로 온다 (#601).
     *
     * 전에는 `List` 를 그대로 되돌렸다 — 공개 목록은 처음부터 `size` 를 받는데 내 것만
     * 안 받아서, 문제집이 늘어나면 **한 번에 다 실어 보낸다.**
     */
    @Test
    fun `내 문제집 목록은 페이지로 온다`() {
        repeat(3) { create(ownerToken, problems = listOf(1)) }

        mockMvc.perform(
            get("/api/v1/collections/me?size=2").header("Authorization", "Bearer $ownerToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.content.length()").value(2))

        mockMvc.perform(
            get("/api/v1/collections/me?size=2&page=1").header("Authorization", "Bearer $ownerToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
    }

    @Test
    fun `만드는 중에는 비어 있어도 된다`() {
        // 이름만 정해 두고 나중에 채우는 흐름을 막으면 안 된다.
        mockMvc.perform(
            post("/api/v1/collections")
                .header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"나중에 채울 목록","visibility":"PRIVATE","problemIds":[]}"""),
        ).andExpect(status().isCreated)
    }

    @Test
    fun `문제가 둘 미만이면 공유할 수 없다`() {
        // 1개짜리 묶음은 묶음이 아니다 — 문제 하나를 보내려면 문제 링크를 보내면 된다.
        mockMvc.perform(
            post("/api/v1/collections")
                .header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"하나뿐","visibility":"UNLISTED","problemIds":[1]}"""),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `같은 문제를 두 번 담을 수 없다`() {
        mockMvc.perform(
            post("/api/v1/collections")
                .header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"중복","visibility":"PRIVATE","problemIds":[1,1]}"""),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `담은 순서가 그대로 유지된다`() {
        val id = create(ownerToken, problems = listOf(3, 1, 2))

        mockMvc.perform(get("/api/v1/collections/$id").header("Authorization", "Bearer $ownerToken"))
            .andExpect(jsonPath("$.problems[0].slug").value("c"))
            .andExpect(jsonPath("$.problems[1].slug").value("a"))
            .andExpect(jsonPath("$.problems[2].slug").value("b"))
    }

    @Test
    fun `비공개 문제집은 남에게 없는 것과 같다`() {
        val id = create(ownerToken, problems = listOf(1, 2))

        // 존재 여부조차 알리지 않는다.
        mockMvc.perform(get("/api/v1/collections/$id").header("Authorization", "Bearer $otherToken"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `링크를 아는 사람은 로그인 없이도 본다`() {
        val id = create(ownerToken, problems = listOf(1, 2), visibility = "UNLISTED")
        val token = shareTokenOf(id)

        mockMvc.perform(get("/api/v1/collections/shared/$token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.summary.name").value("내 문제집"))
            .andExpect(jsonPath("$.editable").value(false))
    }

    @Test
    fun `공유 토큰은 주인에게만 내려간다`() {
        val id = create(ownerToken, problems = listOf(1, 2), visibility = "UNLISTED")
        val token = shareTokenOf(id)

        mockMvc.perform(get("/api/v1/collections/$id").header("Authorization", "Bearer $ownerToken"))
            .andExpect(jsonPath("$.summary.shareToken").isNotEmpty)

        // 링크를 아는 사람만 볼 수 있다는 말이 성립하려면 남에게 토큰을 주면 안 된다.
        mockMvc.perform(get("/api/v1/collections/shared/$token"))
            .andExpect(jsonPath("$.summary.shareToken").doesNotExist())
    }

    @Test
    fun `남의 문제집을 고칠 수 없다`() {
        val id = create(ownerToken, problems = listOf(1, 2), visibility = "UNLISTED")

        mockMvc.perform(
            put("/api/v1/collections/$id")
                .header("Authorization", "Bearer $otherToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"가로채기","visibility":"UNLISTED","problemIds":[1,2]}"""),
        ).andExpect(status().isNotFound)

        mockMvc.perform(delete("/api/v1/collections/$id").header("Authorization", "Bearer $otherToken"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `푼 문제가 진행률로 잡힌다`() {
        solve(problemId = 1)
        val id = create(ownerToken, problems = listOf(1, 2))

        mockMvc.perform(get("/api/v1/collections/$id").header("Authorization", "Bearer $ownerToken"))
            .andExpect(jsonPath("$.summary.solvedCount").value(1))
            .andExpect(jsonPath("$.problems[0].solved").value(true))
            .andExpect(jsonPath("$.problems[1].solved").value(false))
    }

    @Test
    fun `삭제된 문제는 목록에서 빠지고 행은 남는다`() {
        val id = create(ownerToken, problems = listOf(1, 2))
        jdbcClient.sql("UPDATE problems SET deleted_at = now() WHERE id = 1").update()

        mockMvc.perform(get("/api/v1/collections/$id").header("Authorization", "Bearer $ownerToken"))
            .andExpect(jsonPath("$.problems.length()").value(1))
            .andExpect(jsonPath("$.problems[0].slug").value("b"))

        // 복구되면 다시 나타나야 한다. 여기서 지우면 순서 정보까지 사라진다 (ADR-0007).
        val rows = jdbcClient.sql("SELECT count(*) FROM problem_collection_items WHERE collection_id = :id")
            .param("id", id).query(Int::class.java).single()
        kotlin.test.assertEquals(2, rows)
    }

    private fun create(token: String, problems: List<Long>, visibility: String = "PRIVATE"): Long {
        val response = mockMvc.perform(
            post("/api/v1/collections")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"name":"내 문제집","description":"설명","visibility":"$visibility",""" +
                        """"problemIds":[${problems.joinToString(",")}]}""",
                ),
        ).andExpect(status().isCreated).andReturn().response.contentAsString

        return Regex("\"id\":(\\d+)").find(response)!!.groupValues[1].toLong()
    }

    private fun shareTokenOf(id: Long): String {
        val token = jdbcClient.sql("SELECT share_token FROM problem_collections WHERE id = :id")
            .param("id", id).query(String::class.java).single()
        assertNotNull(token)
        return token
    }

    private fun solve(problemId: Long) {
        jdbcClient.sql(
            """
            INSERT INTO submissions
                (user_id, problem_id, runtime_id, source_code, status, verdict, kind, total_count)
            VALUES (:userId, :problemId, 'python:3.12', 'print(3)', 'COMPLETED', 'ACCEPTED', 'USER', 1)
            """,
        ).param("userId", ownerId).param("problemId", problemId).update()
    }
}
