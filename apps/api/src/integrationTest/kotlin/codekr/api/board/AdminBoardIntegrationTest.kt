package codekr.api.board

import codekr.api.auth.security.JwtTokenProvider
import codekr.api.support.IntegrationTestBase
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import codekr.api.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 게시판 관리 (#336).
 *
 * **"지울 수 있다" 와 "지울 것을 찾을 수 있다" 는 다르다.** 여기서 확인하는 것은
 * 찾을 수 있는가와, 지운 사실이 남는가다.
 */
class AdminBoardIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var jdbc: JdbcClient

    private lateinit var managerToken: String
    private lateinit var userToken: String
    private var authorId: Long = 0
    private var postId: Long = 0
    private var commentId: Long = 0

    @BeforeEach
    fun setUp() {
        managerToken = tokenProvider.issueAccessToken(
            userRepository.save(User("m@codekr.dev", "x", "운영자", setOf(UserRole.USER, UserRole.BOARD_MANAGER))),
        )
        val author = userRepository.save(User("a@codekr.dev", "x", "글쓴이", setOf(UserRole.USER)))
        authorId = author.id
        userToken = tokenProvider.issueAccessToken(author)

        postId = jdbc.sql(
            """
            INSERT INTO posts (board, author_id, title, body)
            VALUES ('FREE', :authorId, '문제 되는 글', '본문') RETURNING id
            """,
        ).param("authorId", authorId).query(Long::class.java).single()

        commentId = jdbc.sql(
            """
            INSERT INTO comments (post_id, author_id, body)
            VALUES (:postId, :authorId, '문제 되는 댓글') RETURNING id
            """,
        ).param("postId", postId).param("authorId", authorId).query(Long::class.java).single()
    }

    @Test
    fun `글을 게시판 구분 없이 한 곳에서 본다`() {
        mockMvc.perform(get("/api/v1/admin/board/posts").header("Authorization", "Bearer $managerToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].title").value("문제 되는 글"))
            .andExpect(jsonPath("$.content[0].authorNickname").value("글쓴이"))
            .andExpect(jsonPath("$.content[0].commentCount").value(1))
    }

    @Test
    fun `댓글을 글 밖에서 훑는다`() {
        // **글 안에 숨어 있으면 안 된다** — 어디에 무엇이 달렸는지 알 방법이 없었다.
        mockMvc.perform(get("/api/v1/admin/board/comments").header("Authorization", "Bearer $managerToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].excerpt").value("문제 되는 댓글"))
            .andExpect(jsonPath("$.content[0].postTitle").value("문제 되는 글"))
    }

    @Test
    fun `작성자로 거른다`() {
        mockMvc.perform(
            get("/api/v1/admin/board/posts").param("authorNickname", "없는사람")
                .header("Authorization", "Bearer $managerToken"),
        ).andExpect(jsonPath("$.content.length()").value(0))

        mockMvc.perform(
            get("/api/v1/admin/board/posts").param("authorNickname", "글쓴")
                .header("Authorization", "Bearer $managerToken"),
        ).andExpect(jsonPath("$.content.length()").value(1))
    }

    @Test
    fun `글을 내리면 기록이 남는다`() {
        mockMvc.perform(
            delete("/api/v1/admin/board/posts/$postId").param("reason", "광고")
                .header("Authorization", "Bearer $managerToken"),
        ).andExpect(status().isNoContent)

        // 목록에서 빠진다.
        mockMvc.perform(get("/api/v1/admin/board/posts").header("Authorization", "Bearer $managerToken"))
            .andExpect(jsonPath("$.content.length()").value(0))

        // **남의 글을 지우는 일이므로 기록이 없으면 안 된다** (#225).
        val rows = jdbc.sql("SELECT action, reason, detail FROM admin_audit_logs WHERE target_id = :id")
            .param("id", authorId)
            .query { rs, _ -> listOf(rs.getString("action"), rs.getString("reason"), rs.getString("detail")) }
            .list()
        kotlin.test.assertEquals(listOf(listOf("POST_DELETE", "광고", "문제 되는 글")), rows)
    }

    @Test
    fun `댓글도 지우고 기록한다`() {
        mockMvc.perform(
            delete("/api/v1/admin/board/comments/$commentId").param("reason", "욕설")
                .header("Authorization", "Bearer $managerToken"),
        ).andExpect(status().isNoContent)

        mockMvc.perform(get("/api/v1/admin/board/comments").header("Authorization", "Bearer $managerToken"))
            .andExpect(jsonPath("$.content.length()").value(0))

        val actions = jdbc.sql("SELECT action FROM admin_audit_logs WHERE target_id = :id")
            .param("id", authorId).query(String::class.java).list()
        kotlin.test.assertEquals(listOf("COMMENT_DELETE"), actions)
    }

    @Test
    fun `사유 없이는 내릴 수 없다`() {
        mockMvc.perform(
            delete("/api/v1/admin/board/posts/$postId").header("Authorization", "Bearer $managerToken"),
        ).andExpect(status().isBadRequest)

        // 막혔으므로 글은 그대로다.
        mockMvc.perform(get("/api/v1/admin/board/posts").header("Authorization", "Bearer $managerToken"))
            .andExpect(jsonPath("$.content.length()").value(1))
    }

    @Test
    fun `일반 사용자는 목록을 볼 수 없다`() {
        mockMvc.perform(get("/api/v1/admin/board/posts").header("Authorization", "Bearer $userToken"))
            .andExpect(status().isForbidden)
    }
}
