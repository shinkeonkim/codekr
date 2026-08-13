package codekr.api.collection

import codekr.api.auth.security.JwtTokenProvider
import codekr.api.support.IntegrationTestBase
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import codekr.api.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 어드민 문제집 관리 (#393).
 *
 * **남에게 보여지는 것만 본다.** 남이 혼자 쓰는 목록을 들여다보는 것은 다른 문제이고,
 * 이 화면이 푸는 문제가 아니다.
 */
class AdminCollectionIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var jdbcClient: JdbcClient

    private var ownerId: Long = 0
    private lateinit var adminToken: String
    private lateinit var userToken: String

    @BeforeEach
    fun setUp() {
        ownerId = userRepository.save(User("owner@codekr.dev", "x", "주인", setOf(UserRole.USER))).id
        adminToken = tokenProvider.issueAccessToken(
            userRepository.save(User("admin@codekr.dev", "x", "관리자", setOf(UserRole.USER, UserRole.ADMIN))),
        )
        userToken = tokenProvider.issueAccessToken(
            userRepository.save(User("other@codekr.dev", "x", "남", setOf(UserRole.USER))),
        )
    }

    @Test
    fun `남에게 보여지는 문제집을 목록으로 훑는다`() {
        collection("공개한 것", "PUBLIC")
        collection("링크로 나눈 것", "UNLISTED")
        collection("혼자 쓰는 것", "PRIVATE")

        mockMvc.perform(get("/api/v1/admin/collections").header("Authorization", "Bearer $adminToken"))
            .andExpect(status().isOk)
            // **비공개는 없다.** 셋을 만들었지만 둘만 보인다.
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content[?(@.name == '혼자 쓰는 것')]").isEmpty)
            .andExpect(jsonPath("$.content[0].ownerNickname").value("주인"))
    }

    @Test
    fun `범위로 좁힌다`() {
        collection("공개한 것", "PUBLIC")
        collection("링크로 나눈 것", "UNLISTED")

        mockMvc.perform(
            get("/api/v1/admin/collections").param("visibility", "PUBLIC")
                .header("Authorization", "Bearer $adminToken"),
        )
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].name").value("공개한 것"))
    }

    @Test
    fun `비공개로 좁혀 부르면 아무것도 나오지 않는다`() {
        /*
          **막는 것이 아니라 없는 것이다.** 400 으로 거절하면 "비공개를 보는 길이
          어딘가 있다" 는 인상을 준다 — 조건 자체가 비공개를 빼고 시작한다.
        */
        collection("혼자 쓰는 것", "PRIVATE")

        mockMvc.perform(
            get("/api/v1/admin/collections").param("visibility", "PRIVATE")
                .header("Authorization", "Bearer $adminToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(0))
    }

    @Test
    fun `담긴 문제까지 본다`() {
        // **무엇이 문제인지는 내용을 봐야 안다** — 이름만 보고 내리면 잘못 내린다.
        val id = collection("공개한 것", "PUBLIC")
        problem(1, "two-sum", "두 수의 합")
        problem(2, "reverse", "뒤집기")
        item(id, 1, seq = 1)
        item(id, 2, seq = 2)

        mockMvc.perform(get("/api/v1/admin/collections/$id").header("Authorization", "Bearer $adminToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.problems.length()").value(2))
            .andExpect(jsonPath("$.problems[0].title").value("두 수의 합"))
            .andExpect(jsonPath("$.ownerNickname").value("주인"))
    }

    @Test
    fun `비공개 문제집의 상세는 없는 것으로 다룬다`() {
        val id = collection("혼자 쓰는 것", "PRIVATE")

        mockMvc.perform(get("/api/v1/admin/collections/$id").header("Authorization", "Bearer $adminToken"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `어드민이 아니면 볼 수 없다`() {
        collection("공개한 것", "PUBLIC")

        mockMvc.perform(get("/api/v1/admin/collections").header("Authorization", "Bearer $userToken"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `내리면 비공개가 되고 주인이 안다`() {
        val id = collection("공개한 것", "PUBLIC")

        mockMvc.perform(
            post("/api/v1/admin/collections/$id/takedown").param("reason", "중복")
                .header("Authorization", "Bearer $adminToken"),
        ).andExpect(status().isNoContent)

        // 지우지 않는다 — 비공개로 되돌리면 주인은 그대로 갖고 있는다.
        val visibility = jdbcClient.sql("SELECT visibility FROM problem_collections WHERE id = :id")
            .param("id", id).query(String::class.java).single()
        kotlin.test.assertEquals("PRIVATE", visibility)

        // **말없이 사라지면 고칠 수도 없다.**
        val notifications = jdbcClient.sql("SELECT count(*) FROM notifications WHERE user_id = :id")
            .param("id", ownerId).query(Int::class.java).single()
        kotlin.test.assertEquals(1, notifications)
    }

    private fun collection(name: String, visibility: String): Long =
        jdbcClient.sql(
            """
            INSERT INTO problem_collections (owner_id, name, description, visibility, share_token)
            VALUES (:owner, :name, '', CAST(:visibility AS text),
                    substr(replace(gen_random_uuid()::text, '-', ''), 1, 24))
            RETURNING id
            """,
        )
            .param("owner", ownerId).param("name", name).param("visibility", visibility)
            .query(Long::class.java).single()

    private fun problem(id: Long, slug: String, title: String) {
        jdbcClient.sql(
            """
            INSERT INTO problems (id, slug, title, category, difficulty_level, description, published)
            VALUES (:id, :slug, :title, 'ALGORITHM', 1, '설명', true)
            """,
        ).param("id", id).param("slug", slug).param("title", title).update()
    }

    private fun item(collectionId: Long, problemId: Long, seq: Int) {
        jdbcClient.sql(
            "INSERT INTO problem_collection_items (collection_id, problem_id, seq) VALUES (:c, :p, :s)",
        ).param("c", collectionId).param("p", problemId).param("s", seq).update()
    }
}
