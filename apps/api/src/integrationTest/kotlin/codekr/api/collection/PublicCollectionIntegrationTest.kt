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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 공개 문제집 (#208).
 *
 * 문제집(#87)은 **일부러 공개를 열지 않았다.** 그 판단을 뒤집되, 정리 수단을 함께 둔다 —
 * "알아서 관리한다" 가 성립하려면 **어드민이 실제로 내릴 수 있어야** 한다.
 */
class PublicCollectionIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var jdbc: JdbcClient

    private lateinit var ownerToken: String
    private lateinit var adminToken: String
    private var ownerId: Long = 0
    private var publishedIds: List<Long> = emptyList()
    private var hiddenId: Long = 0

    @BeforeEach
    fun setUp() {
        val owner = userRepository.save(User("owner@codekr.dev", "x", "주인", setOf(UserRole.USER)))
        ownerId = owner.id
        ownerToken = tokenProvider.issueAccessToken(owner)
        adminToken = tokenProvider.issueAccessToken(
            userRepository.save(User("admin@codekr.dev", "x", "관리자", setOf(UserRole.ADMIN))),
        )

        publishedIds = (1..2).map { seq -> insertProblem("pub-$seq", published = true) }
        hiddenId = insertProblem("hidden", published = false)
    }

    @Test
    fun `공개 문제집은 로그인 없이 목록에 보인다`() {
        // 링크 공유만으로는 아무도 새 문제집을 발견할 수 없다.
        create("공개 커리큘럼", "PUBLIC", publishedIds)

        mockMvc.perform(get("/api/v1/collections"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].name").value("공개 커리큘럼"))
            .andExpect(jsonPath("$.content[0].ownerNickname").value("주인"))
    }

    @Test
    fun `비공개와 링크 공유는 목록에 없다`() {
        create("나만 보기", "PRIVATE", publishedIds)
        create("링크만", "UNLISTED", publishedIds)

        mockMvc.perform(get("/api/v1/collections"))
            .andExpect(jsonPath("$.content.length()").value(0))
    }

    @Test
    fun `미공개 문제가 들어 있으면 공개할 수 없다`() {
        // **미공개 문제의 제목이 공개 목록으로 새면 안 된다.**
        mockMvc.perform(
            post("/api/v1/collections").header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("샐 뻔한 것", "PUBLIC", publishedIds + hiddenId)),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `문제가 둘 미만이면 공개할 수 없다`() {
        // 공유 규칙(#87)이 공개에도 그대로 걸린다.
        mockMvc.perform(
            post("/api/v1/collections").header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("하나뿐", "PUBLIC", listOf(publishedIds.first()))),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `어드민이 내리면 비공개가 되고 주인에게 알린다`() {
        val id = create("내려갈 것", "PUBLIC", publishedIds)

        mockMvc.perform(
            post("/api/v1/admin/collections/$id/takedown").param("reason", "광고")
                .header("Authorization", "Bearer $adminToken"),
        ).andExpect(status().isNoContent)

        // 목록에서 빠진다.
        mockMvc.perform(get("/api/v1/collections"))
            .andExpect(jsonPath("$.content.length()").value(0))

        // **지우지 않는다** — 주인은 그대로 갖고 있다.
        val visibility = jdbc.sql("SELECT visibility FROM problem_collections WHERE id = :id")
            .param("id", id).query(String::class.java).single()
        kotlin.test.assertEquals("PRIVATE", visibility)

        // **말없이 사라지면 고칠 수도 없다.**
        val notices = jdbc.sql("SELECT title FROM notifications WHERE user_id = :id")
            .param("id", ownerId).query(String::class.java).list()
        kotlin.test.assertEquals(listOf("문제집이 공개 목록에서 내려갔습니다"), notices)
    }

    @Test
    fun `사유 없이는 내릴 수 없다`() {
        val id = create("내려갈 것", "PUBLIC", publishedIds)

        mockMvc.perform(
            post("/api/v1/admin/collections/$id/takedown").header("Authorization", "Bearer $adminToken"),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `공개가 아닌 것은 내릴 수 없다`() {
        val id = create("링크만", "UNLISTED", publishedIds)

        mockMvc.perform(
            post("/api/v1/admin/collections/$id/takedown").param("reason", "확인")
                .header("Authorization", "Bearer $adminToken"),
        ).andExpect(status().isBadRequest)
    }

    private fun create(name: String, visibility: String, problemIds: List<Long>): Long {
        val response = mockMvc.perform(
            post("/api/v1/collections").header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(name, visibility, problemIds)),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return Regex("\"id\":(\\d+)").find(response)!!.groupValues[1].toLong()
    }

    private fun body(name: String, visibility: String, problemIds: List<Long>) =
        """{"name":"$name","description":"","visibility":"$visibility","problemIds":$problemIds}"""

    private fun insertProblem(slug: String, published: Boolean): Long =
        jdbc.sql(
            """
            INSERT INTO problems (slug, title, category, description, time_limit_ms, memory_limit_mb,
                                  published, difficulty_state)
            VALUES (:slug, :title, 'ALGORITHM', '지문', 2000, 256, :published, 'UNRATED')
            RETURNING id
            """,
        )
            .param("slug", slug).param("title", "문제 $slug").param("published", published)
            .query(Long::class.java).single()
}
