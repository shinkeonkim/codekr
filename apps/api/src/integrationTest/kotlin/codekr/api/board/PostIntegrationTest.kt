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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** 커뮤니티 게시판 (#137). */
class PostIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider

    private lateinit var authorToken: String
    private lateinit var otherToken: String
    private lateinit var moderatorToken: String

    @BeforeEach
    fun setUp() {
        authorToken = tokenProvider.issueAccessToken(
            userRepository.save(User("author@codekr.dev", "x", "글쓴이", setOf(UserRole.USER))),
        )
        otherToken = tokenProvider.issueAccessToken(
            userRepository.save(User("other@codekr.dev", "x", "남", setOf(UserRole.USER))),
        )
        moderatorToken = tokenProvider.issueAccessToken(
            userRepository.save(User("mod@codekr.dev", "x", "운영자", setOf(UserRole.USER, UserRole.BOARD_MANAGER))),
        )
    }

    @Test
    fun `읽기는 로그인 없이도 된다`() {
        create(authorToken, "FREE")

        // 로그인해야 읽을 수 있으면 검색으로 들어온 사람이 아무것도 볼 수 없다.
        mockMvc.perform(get("/api/v1/posts"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].authorNickname").value("글쓴이"))
    }

    @Test
    fun `쓰기는 로그인이 필요하다`() {
        mockMvc.perform(
            post("/api/v1/posts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("FREE", "익명 글")),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `공지는 운영자만 쓴다`() {
        // 아무나 쓸 수 있으면 공지가 아니다.
        mockMvc.perform(
            post("/api/v1/posts")
                .header("Authorization", "Bearer " + authorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("NOTICE", "가짜 공지")),
        ).andExpect(status().isForbidden)

        mockMvc.perform(
            post("/api/v1/posts")
                .header("Authorization", "Bearer " + moderatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("NOTICE", "진짜 공지")),
        ).andExpect(status().isCreated)
    }

    @Test
    fun `쓸 수 있는 게시판을 서버가 알려준다`() {
        // 쓸 수 없는 게시판에 글쓰기 버튼을 보여주면 눌렀을 때 거부당한다.
        mockMvc.perform(get("/api/v1/posts/boards").header("Authorization", "Bearer " + authorToken))
            .andExpect(jsonPath("$[?(@.value == 'NOTICE')].writable").value(false))
            .andExpect(jsonPath("$[?(@.value == 'FREE')].writable").value(true))

        mockMvc.perform(get("/api/v1/posts/boards").header("Authorization", "Bearer " + moderatorToken))
            .andExpect(jsonPath("$[?(@.value == 'NOTICE')].writable").value(true))
    }

    @Test
    fun `남의 글은 고칠 수 없다`() {
        val id = create(authorToken, "FREE")

        mockMvc.perform(
            put("/api/v1/posts/" + id)
                .header("Authorization", "Bearer " + otherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("FREE", "가로채기")),
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `운영자도 남의 글을 고칠 수는 없다`() {
        val id = create(authorToken, "FREE")

        // 고치면 그 사람이 쓴 것으로 남는데, 실제로 쓴 사람은 다른 사람이다.
        mockMvc.perform(
            put("/api/v1/posts/" + id)
                .header("Authorization", "Bearer " + moderatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("FREE", "운영자가 고침")),
        ).andExpect(status().isForbidden)

        mockMvc.perform(get("/api/v1/posts/" + id).header("Authorization", "Bearer " + moderatorToken))
            .andExpect(jsonPath("$.editable").value(false))
            // 다만 내릴 수는 있어야 한다. 신고 도구가 없는 지금은 이것이 유일한 수단이다.
            .andExpect(jsonPath("$.deletable").value(true))
    }

    @Test
    fun `운영자는 남의 글을 내릴 수 있다`() {
        val id = create(authorToken, "FREE")

        mockMvc.perform(delete("/api/v1/posts/" + id).header("Authorization", "Bearer " + moderatorToken))
            .andExpect(status().isNoContent)

        mockMvc.perform(get("/api/v1/posts/" + id)).andExpect(status().isNotFound)
    }

    @Test
    fun `삭제는 소프트 삭제다`() {
        val id = create(authorToken, "FREE")
        mockMvc.perform(delete("/api/v1/posts/" + id).header("Authorization", "Bearer " + authorToken))
            .andExpect(status().isNoContent)

        mockMvc.perform(get("/api/v1/posts")).andExpect(jsonPath("$.content.length()").value(0))
    }

    @Test
    fun `게시판과 검색어로 거를 수 있다`() {
        create(authorToken, "FREE", "자유롭게 씁니다")
        create(authorToken, "QUESTION", "DP 가 막힙니다")

        mockMvc.perform(get("/api/v1/posts").param("board", "QUESTION"))
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].title").value("DP 가 막힙니다"))

        mockMvc.perform(get("/api/v1/posts").param("q", "자유"))
            .andExpect(jsonPath("$.content.length()").value(1))
    }

    @Test
    fun `본문은 마크다운 원문 그대로 저장된다`() {
        // 저장 시점에 HTML 로 바꾸면 렌더링 규칙을 고칠 때 이미 쌓인 글을 전부 다시 만들어야 한다.
        val raw = "# 제목\n\n<script>alert(1)</script>\n\n```py\nprint(1)\n```"
        val id = createWithBody(authorToken, raw)

        mockMvc.perform(get("/api/v1/posts/" + id))
            .andExpect(jsonPath("$.body").value(raw))
    }

    private fun create(token: String, board: String, title: String = "제목"): Long {
        val response = mockMvc.perform(
            post("/api/v1/posts")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(board, title)),
        ).andExpect(status().isCreated).andReturn().response.contentAsString

        return Regex("\"id\":(\\d+)").find(response)!!.groupValues[1].toLong()
    }

    private fun createWithBody(token: String, markdown: String): Long {
        val escaped = markdown.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        val response = mockMvc.perform(
            post("/api/v1/posts")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"board\":\"FREE\",\"title\":\"제목\",\"body\":\"" + escaped + "\"}"),
        ).andExpect(status().isCreated).andReturn().response.contentAsString

        return Regex("\"id\":(\\d+)").find(response)!!.groupValues[1].toLong()
    }

    private fun body(board: String, title: String) =
        "{\"board\":\"" + board + "\",\"title\":\"" + title + "\",\"body\":\"본문입니다\"}"
}
