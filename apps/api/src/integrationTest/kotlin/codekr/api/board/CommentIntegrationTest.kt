package codekr.api.board

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

/** 댓글과 대댓글 (#138). */
class CommentIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var jdbcClient: JdbcClient

    private var authorId: Long = 0
    private lateinit var authorToken: String
    private lateinit var otherToken: String
    private lateinit var moderatorToken: String
    private var postId: Long = 0

    @BeforeEach
    fun setUp() {
        val author = userRepository.save(User("a@codekr.dev", "x", "글쓴이", setOf(UserRole.USER)))
        authorId = author.id
        authorToken = tokenProvider.issueAccessToken(author)
        otherToken = tokenProvider.issueAccessToken(
            userRepository.save(User("b@codekr.dev", "x", "답하는이", setOf(UserRole.USER))),
        )
        moderatorToken = tokenProvider.issueAccessToken(
            userRepository.save(User("m@codekr.dev", "x", "운영자", setOf(UserRole.USER, UserRole.BOARD_MANAGER))),
        )

        postId = jdbcClient.sql(
            """
            INSERT INTO posts (board, author_id, title, body)
            VALUES ('QUESTION', :authorId, '질문', '본문')
            RETURNING id
            """,
        ).param("authorId", authorId).query(Long::class.java).single()
    }

    @Test
    fun `익명 댓글을 받지 않는다`() {
        mockMvc.perform(
            post("/api/v1/posts/" + postId + "/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"익명\"}"),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `깊이 제한 없이 이어진다`() {
        // "대댓글까지만" 으로 막으면 세 번째 발언부터는 누구에게 하는 말인지 사라진다.
        val first = comment(authorToken, null, "질문입니다")
        val second = comment(otherToken, first, "이렇게 해보세요")
        val third = comment(authorToken, second, "그래도 안 됩니다")
        val fourth = comment(otherToken, third, "그럼 이건요?")

        // **저장에는 깊이 제한이 없다** (#138). 다만 내려보낼 때는 자른다 (#213) —
        // 기본 응답에서는 깊은 자리가 접히고, 남은 개수만 보인다.
        mockMvc.perform(get("/api/v1/posts/" + postId + "/comments"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.comments.length()").value(1))
            .andExpect(jsonPath("$.comments[0].children[0].children[0].children.length()").value(0))
            .andExpect(jsonPath("$.comments[0].children[0].children[0].remainingChildren").value(1))
            // 전체 수는 잘라 내려도 정확하다 — 서버가 센다.
            .andExpect(jsonPath("$.totalCount").value(4))

        // 알림·링크로 들어오면 그 자리가 보이도록 조상을 편다 (#212).
        mockMvc.perform(get("/api/v1/posts/" + postId + "/comments").param("around", fourth.toString()))
            .andExpect(jsonPath("$.comments[0].children[0].children[0].children[0].body").value("그럼 이건요?"))
    }

    @Test
    fun `자식이 있는 댓글을 지워도 자식은 남는다`() {
        val parent = comment(authorToken, null, "지워질 댓글")
        comment(otherToken, parent, "남아야 할 답")

        mockMvc.perform(delete("/api/v1/comments/" + parent).header("Authorization", "Bearer " + authorToken))
            .andExpect(status().isOk)

        // 자식까지 지우면 남의 글이 함께 사라진다.
        mockMvc.perform(get("/api/v1/posts/" + postId + "/comments"))
            .andExpect(jsonPath("$.comments[0].deleted").value(true))
            .andExpect(jsonPath("$.comments[0].body").doesNotExist())
            .andExpect(jsonPath("$.comments[0].authorNickname").doesNotExist())
            .andExpect(jsonPath("$.comments[0].children[0].body").value("남아야 할 답"))
    }

    @Test
    fun `자식이 없는 삭제된 댓글은 아예 사라진다`() {
        val id = comment(authorToken, null, "혼자 있는 댓글")

        mockMvc.perform(delete("/api/v1/comments/" + id).header("Authorization", "Bearer " + authorToken))
            .andExpect(status().isOk)

        // 자리만 차지할 이유가 없다.
        mockMvc.perform(get("/api/v1/posts/" + postId + "/comments"))
            .andExpect(jsonPath("$.comments.length()").value(0))
    }

    @Test
    fun `삭제된 댓글에는 답할 수 없다`() {
        val id = comment(authorToken, null, "곧 지움")
        comment(otherToken, id, "자식")
        mockMvc.perform(delete("/api/v1/comments/" + id).header("Authorization", "Bearer " + authorToken))

        // 무엇에 답하는지가 사라진 자리다.
        mockMvc.perform(
            post("/api/v1/posts/" + postId + "/comments")
                .header("Authorization", "Bearer " + otherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentId\":" + id + ",\"body\":\"뒤늦은 답\"}"),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `다른 글의 댓글에 답할 수 없다`() {
        val otherPost = jdbcClient.sql(
            "INSERT INTO posts (board, author_id, title, body) VALUES ('FREE', :a, '다른 글', '본문') RETURNING id",
        ).param("a", authorId).query(Long::class.java).single()
        val id = comment(authorToken, null, "이 글의 댓글")

        // 그러면 트리가 두 글에 걸친다.
        mockMvc.perform(
            post("/api/v1/posts/" + otherPost + "/comments")
                .header("Authorization", "Bearer " + otherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentId\":" + id + ",\"body\":\"엉뚱한 답\"}"),
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `운영자는 남의 댓글을 내릴 수 있지만 고칠 수는 없다`() {
        val id = comment(authorToken, null, "원문")

        mockMvc.perform(
            put("/api/v1/comments/" + id)
                .header("Authorization", "Bearer " + moderatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"운영자가 고침\"}"),
        ).andExpect(status().isForbidden)

        mockMvc.perform(delete("/api/v1/comments/" + id).header("Authorization", "Bearer " + moderatorToken))
            .andExpect(status().isOk)
    }

    @Test
    fun `목록에 댓글 수가 나온다`() {
        comment(authorToken, null, "하나")
        val second = comment(otherToken, null, "둘")
        comment(authorToken, second, "셋")

        // 답이 달렸는지가 목록에서 보여야 질문 글이 쓸모가 있다.
        mockMvc.perform(get("/api/v1/posts"))
            .andExpect(jsonPath("$.content[0].commentCount").value(3))

        mockMvc.perform(delete("/api/v1/comments/" + second).header("Authorization", "Bearer " + otherToken))
        mockMvc.perform(get("/api/v1/posts"))
            .andExpect(jsonPath("$.content[0].commentCount").value(2))
    }

    @Test
    fun `삭제된 댓글은 본문을 내리지 않는다`() {
        val id = comment(authorToken, null, "비밀이 담긴 댓글")
        comment(otherToken, id, "자식")
        mockMvc.perform(delete("/api/v1/comments/" + id).header("Authorization", "Bearer " + authorToken))

        val body = mockMvc.perform(get("/api/v1/posts/" + postId + "/comments"))
            .andReturn().response.contentAsString
        // 본문은 DB 에 남지만(복구 가능해야 한다) 화면으로는 내려가지 않아야 한다.
        kotlin.test.assertTrue(!body.contains("비밀이 담긴"))
    }

    private fun comment(token: String, parentId: Long?, body: String): Long {
        val payload = if (parentId == null) {
            "{\"body\":\"" + body + "\"}"
        } else {
            "{\"parentId\":" + parentId + ",\"body\":\"" + body + "\"}"
        }
        mockMvc.perform(
            post("/api/v1/posts/" + postId + "/comments")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload),
        ).andExpect(status().isOk)

        return jdbcClient.sql("SELECT max(id) FROM comments").query(Long::class.java).single()
    }
    @Test
    fun `고치면 고친 시각이 함께 온다`() {
        // **`edited` 만으로는 부족하다** (#211). 답이 달린 뒤에 원글을 고치면 대화가
        // 어긋나 보이는데, 언제 고쳤는지가 없으면 답글 쓴 사람이 잘못 읽은 것처럼 된다.
        val id = comment(authorToken, null, "처음 쓴 내용")

        mockMvc.perform(get("/api/v1/posts/" + postId + "/comments").header("Authorization", "Bearer " + authorToken))
            .andExpect(jsonPath("$.comments[0].edited").value(false))
            .andExpect(jsonPath("$.comments[0].editedAt").doesNotExist())

        // 5초 유예를 넘겨야 수정으로 친다 — 저장 직후의 시각 차이를 거르는 값이다.
        jdbcClient.sql("UPDATE comments SET created_at = created_at - interval '1 hour' WHERE id = :id")
            .param("id", id).update()

        mockMvc.perform(
            put("/api/v1/comments/" + id).header("Authorization", "Bearer " + authorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"body":"고친 내용"}"""),
        ).andExpect(status().isOk)

        mockMvc.perform(get("/api/v1/posts/" + postId + "/comments").header("Authorization", "Bearer " + authorToken))
            .andExpect(jsonPath("$.comments[0].body").value("고친 내용"))
            .andExpect(jsonPath("$.comments[0].edited").value(true))
            .andExpect(jsonPath("$.comments[0].editedAt").exists())
    }

    @Test
    fun `내 글에 댓글이 달리면 알린다`() {
        comment(otherToken, null, "답합니다")

        val rows = jdbcClient.sql(
            "SELECT category, title, link FROM notifications WHERE user_id = :id",
        ).param("id", authorId).query { rs, _ ->
            listOf(rs.getString("category"), rs.getString("title"), rs.getString("link"))
        }.list()

        kotlin.test.assertEquals(1, rows.size)
        kotlin.test.assertEquals("COMMENT", rows[0][0])
        kotlin.test.assertEquals("내 글에 댓글이 달렸습니다", rows[0][1])
        // **그 댓글 자리로 간다** — 글만 열면 긴 스레드에서 다시 찾아야 한다.
        kotlin.test.assertTrue(rows[0][2]!!.startsWith("/posts/" + postId + "#comment-"))
    }

    @Test
    fun `내 댓글에 답이 달리면 문구가 다르다`() {
        val mine = comment(authorToken, null, "질문에 붙이는 내 댓글")
        comment(otherToken, mine, "그 댓글에 답")

        val titles = jdbcClient.sql("SELECT title FROM notifications WHERE user_id = :id")
            .param("id", authorId).query(String::class.java).list()

        // 내 글에 단 내 댓글은 알리지 않으므로, 답글 하나만 남는다.
        kotlin.test.assertEquals(listOf("내 댓글에 답이 달렸습니다"), titles)
    }

    @Test
    fun `자기 글에 자기가 단 댓글은 알리지 않는다`() {
        comment(authorToken, null, "내 글에 내가 단다")

        val count = jdbcClient.sql("SELECT count(*) FROM notifications WHERE user_id = :id")
            .param("id", authorId).query(Int::class.java).single()
        kotlin.test.assertEquals(0, count)
    }

    @Test
    fun `최상위가 많으면 잘라서 내리고 커서로 이어받는다`() {
        // 긴 스레드 하나가 다른 사람들의 댓글을 화면 밖으로 밀어내면 안 된다 (#213).
        repeat(25) { comment(authorToken, null, "댓글 " + it) }

        val body = mockMvc.perform(get("/api/v1/posts/" + postId + "/comments"))
            .andExpect(jsonPath("$.comments.length()").value(20))
            .andExpect(jsonPath("$.totalCount").value(25))
            .andExpect(jsonPath("$.remainingTop").value(5))
            .andReturn().response.contentAsString

        // **커서는 마지막으로 받은 id 다.** 오프셋이면 읽는 사이에 새 댓글이 달릴 때
        // 이미 본 것을 다시 받거나 건너뛴다.
        val last = Regex("\"id\":(\\d+)").findAll(body).last().groupValues[1]

        mockMvc.perform(get("/api/v1/posts/" + postId + "/comments").param("after", last))
            .andExpect(jsonPath("$.comments.length()").value(5))
            .andExpect(jsonPath("$.remainingTop").value(0))
    }

    @Test
    fun `이어받는 사이에 새 댓글이 달려도 겹치지 않는다`() {
        repeat(21) { comment(authorToken, null, "댓글 " + it) }

        val first = mockMvc.perform(get("/api/v1/posts/" + postId + "/comments"))
            .andReturn().response.contentAsString
        val firstIds = Regex("\"id\":(\\d+)").findAll(first).map { it.groupValues[1] }.toList()

        // 읽는 사이에 새 댓글이 달린다.
        comment(otherToken, null, "그사이에 달린 댓글")

        val next = mockMvc.perform(
            get("/api/v1/posts/" + postId + "/comments").param("after", firstIds.last()),
        ).andReturn().response.contentAsString
        val nextIds = Regex("\"id\":(\\d+)").findAll(next).map { it.groupValues[1] }.toList()

        kotlin.test.assertTrue(firstIds.intersect(nextIds.toSet()).isEmpty(), "같은 댓글이 두 번 나왔다")
        kotlin.test.assertEquals(2, nextIds.size)
    }

    @Test
    fun `한 부모의 답글도 잘라서 내리고 이어받는다`() {
        val parent = comment(authorToken, null, "부모")
        repeat(5) { comment(otherToken, parent, "답 " + it) }

        mockMvc.perform(get("/api/v1/posts/" + postId + "/comments"))
            .andExpect(jsonPath("$.comments[0].children.length()").value(3))
            // 접힌 자리에 몇 개가 남았는지 — 없으면 펼칠 이유를 모른다.
            .andExpect(jsonPath("$.comments[0].remainingChildren").value(2))

        val body = mockMvc.perform(get("/api/v1/posts/" + postId + "/comments"))
            .andReturn().response.contentAsString
        val thirdChild = Regex("\"id\":(\\d+)").findAll(body).map { it.groupValues[1] }.toList()[3]

        mockMvc.perform(get("/api/v1/comments/" + parent + "/children").param("after", thirdChild))
            .andExpect(jsonPath("$.comments.length()").value(2))
            .andExpect(jsonPath("$.remainingTop").value(0))
    }

    @Test
    fun `방금 쓴 댓글은 접힌 자리에 있어도 보인다`() {
        val a = comment(authorToken, null, "1단")
        val b = comment(authorToken, a, "2단")
        val c = comment(authorToken, b, "3단")

        // 4단은 기본 응답에서 접히는 깊이인데, 쓴 사람에게는 보여야 한다.
        mockMvc.perform(
            post("/api/v1/posts/" + postId + "/comments")
                .header("Authorization", "Bearer " + authorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentId\":" + c + ",\"body\":\"4단\"}"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.comments[0].children[0].children[0].children[0].body").value("4단"))
    }

}
