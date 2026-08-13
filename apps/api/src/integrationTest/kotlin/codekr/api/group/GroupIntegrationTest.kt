package codekr.api.group

import codekr.api.auth.security.JwtTokenProvider
import codekr.api.group.entity.GROUP_MEMBER_LIMIT
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 그룹 만들기·초대·가입 (#401, #240 6단계).
 *
 * **소속과 다른 것이다** (기획서 2절). 누구나 만들고 사람이 사람을 부른다.
 */
class GroupIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var jdbcClient: JdbcClient
    @Autowired private lateinit var tokenProvider: JwtTokenProvider

    private val tokens = mutableMapOf<Long, String>()
    private var owner: Long = 0
    private var friend: Long = 0
    private var stranger: Long = 0

    @BeforeEach
    fun setUp() {
        owner = user("owner", "방장")
        friend = user("friend", "친구")
        stranger = user("stranger", "남")
    }

    @Test
    fun `만든 사람이 방장이고 멤버다`() {
        // **방장도 멤버다.** 아니면 "내 그룹" 목록과 인원 수가 방장만 다르게 센다.
        val id = createGroup()

        mockMvc.perform(get("/api/v1/groups/$id").withUser(owner))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.owner").value(true))
            .andExpect(jsonPath("$.memberCount").value(1))
            .andExpect(jsonPath("$.members[0].nickname").value("방장"))
            .andExpect(jsonPath("$.memberLimit").value(GROUP_MEMBER_LIMIT))

        mockMvc.perform(get("/api/v1/groups").withUser(owner))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].owner").value(true))
    }

    @Test
    fun `초대 링크는 방장에게만 간다`() {
        // 멤버 아무나 부를 수 있으면 방장이 인원을 통제할 길이 없다.
        val id = createGroup()
        join(id, friend)

        mockMvc.perform(get("/api/v1/groups/$id").withUser(owner))
            .andExpect(jsonPath("$.inviteToken").isNotEmpty)
        mockMvc.perform(get("/api/v1/groups/$id").withUser(friend))
            .andExpect(jsonPath("$.inviteToken").doesNotExist())
    }

    @Test
    fun `초대 링크로 들어온다`() {
        val id = createGroup()
        val token = inviteToken(id)

        // 가입 전에 무엇에 들어가는지 본다.
        mockMvc.perform(get("/api/v1/groups/invites/$token").withUser(friend))
            .andExpect(jsonPath("$.name").value("알고리즘 스터디"))
            .andExpect(jsonPath("$.member").value(false))

        mockMvc.perform(post("/api/v1/groups/invites/$token/join").withUser(friend))
            .andExpect(status().isOk)
        mockMvc.perform(get("/api/v1/groups/$id").withUser(friend))
            .andExpect(jsonPath("$.memberCount").value(2))
    }

    @Test
    fun `링크를 새로 뽑으면 옛 링크는 죽는다`() {
        val id = createGroup()
        val old = inviteToken(id)

        mockMvc.perform(post("/api/v1/groups/$id/invite").withUser(owner))
            .andExpect(status().isOk)

        mockMvc.perform(post("/api/v1/groups/invites/$old/join").withUser(friend))
            .andExpect(status().isNotFound)
        mockMvc.perform(post("/api/v1/groups/invites/${inviteToken(id)}/join").withUser(friend))
            .andExpect(status().isOk)
    }

    @Test
    fun `공개 가입은 방장이 켜야 열린다`() {
        // **초대 링크가 기본이다.** 처음부터 공개면 스팸 가입이 온다 (기획서 5절).
        val id = createGroup()

        mockMvc.perform(post("/api/v1/groups/$id/members").withUser(stranger))
            .andExpect(status().isForbidden)

        mockMvc.perform(
            patch("/api/v1/groups/$id").withUser(owner).contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"알고리즘 스터디","description":"","openJoin":true}"""),
        ).andExpect(status().isOk)

        mockMvc.perform(post("/api/v1/groups/$id/members").withUser(stranger))
            .andExpect(status().isOk)
    }

    @Test
    fun `멤버가 아니면 명단을 볼 수 없다`() {
        // 이름과 인원까지는 초대 링크가 보여 준다. 명단은 그 안의 일이다.
        val id = createGroup()

        mockMvc.perform(get("/api/v1/groups/$id").withUser(stranger))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `방장은 넘기고 나간다`() {
        // 방장이 그냥 나가면 이름도 못 고치고 링크도 못 뽑는 그룹이 남는다.
        val id = createGroup()
        join(id, friend)

        mockMvc.perform(delete("/api/v1/groups/$id/members/me").withUser(owner))
            .andExpect(status().isBadRequest)

        mockMvc.perform(post("/api/v1/groups/$id/owner/$friend").withUser(owner))
            .andExpect(status().isNoContent)
        mockMvc.perform(delete("/api/v1/groups/$id/members/me").withUser(owner))
            .andExpect(status().isNoContent)

        mockMvc.perform(get("/api/v1/groups/$id").withUser(friend))
            .andExpect(jsonPath("$.owner").value(true))
            .andExpect(jsonPath("$.memberCount").value(1))
    }

    @Test
    fun `밖의 사람에게는 방장을 넘길 수 없다`() {
        val id = createGroup()

        mockMvc.perform(post("/api/v1/groups/$id/owner/$stranger").withUser(owner))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `방장만 내보낼 수 있고 자기 자신은 못 내보낸다`() {
        val id = createGroup()
        join(id, friend)

        mockMvc.perform(delete("/api/v1/groups/$id/members/$owner").withUser(friend))
            .andExpect(status().isForbidden)
        mockMvc.perform(delete("/api/v1/groups/$id/members/$owner").withUser(owner))
            .andExpect(status().isBadRequest)

        mockMvc.perform(delete("/api/v1/groups/$id/members/$friend").withUser(owner))
            .andExpect(status().isNoContent)
        mockMvc.perform(get("/api/v1/groups/$id").withUser(owner))
            .andExpect(jsonPath("$.memberCount").value(1))
    }

    @Test
    fun `인원 상한을 넘기지 못한다`() {
        // 없으면 **"전체 랭킹" 을 흉내 내는 그룹**이 생긴다 (기획서 5절).
        val id = createGroup()
        jdbcClient.sql(
            """
            INSERT INTO users (email, password_hash, nickname)
            SELECT 'bulk' || i || '@codekr.dev', 'x', '무리' || i
            FROM generate_series(1, :n) i
            """,
        ).param("n", GROUP_MEMBER_LIMIT).update()
        jdbcClient.sql(
            """
            INSERT INTO group_members (group_id, user_id)
            SELECT :g, id FROM users WHERE email LIKE 'bulk%' LIMIT :n
            """,
        ).param("g", id).param("n", GROUP_MEMBER_LIMIT - 1).update()

        // 방장 + 199 명 = 딱 200 명. 한 사람 더는 안 된다.
        mockMvc.perform(post("/api/v1/groups/invites/${inviteToken(id)}/join").withUser(friend))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `해산하면 링크도 죽고 목록에서도 사라진다`() {
        val id = createGroup()
        val token = inviteToken(id)
        join(id, friend)

        mockMvc.perform(delete("/api/v1/groups/$id").withUser(owner))
            .andExpect(status().isNoContent)

        mockMvc.perform(get("/api/v1/groups/$id").withUser(owner)).andExpect(status().isNotFound)
        mockMvc.perform(get("/api/v1/groups/invites/$token").withUser(stranger))
            .andExpect(status().isNotFound)
        mockMvc.perform(get("/api/v1/groups").withUser(friend))
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `방장만 이름을 고칠 수 있다`() {
        val id = createGroup()
        join(id, friend)

        mockMvc.perform(
            patch("/api/v1/groups/$id").withUser(friend).contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"내가 가로챈 이름","description":"","openJoin":false}"""),
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `소속 이름과 같아도 만들 수 있다`() {
        /*
          **막지 않는다** (기획서 5절). 막으려면 기관 이름 목록이 필요하고 그것은
          유지될 수 없다. 대신 화면이 소속과 그룹을 절대 같은 목록에 섞지 않는다.
        */
        jdbcClient.sql("INSERT INTO affiliations (name, kind) VALUES ('서울대학교', 'SCHOOL')").update()

        mockMvc.perform(
            post("/api/v1/groups").withUser(stranger).contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"서울대학교","description":"","openJoin":false}"""),
        ).andExpect(status().isCreated)
    }

    private fun user(prefix: String, nickname: String): Long {
        val saved = userRepository.save(User("$prefix@codekr.dev", "x", nickname, setOf(UserRole.USER)))
        tokens[saved.id] = tokenProvider.issueAccessToken(saved)
        return saved.id
    }

    /** 그 사람으로 부른다. 그룹 API 는 전부 로그인이 필요하다. */
    private fun MockHttpServletRequestBuilder.withUser(userId: Long): MockHttpServletRequestBuilder =
        header("Authorization", "Bearer ${tokens[userId]}")

    private fun createGroup(): Long {
        val body = mockMvc.perform(
            post("/api/v1/groups").withUser(owner).contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"알고리즘 스터디","description":"매주 화요일","openJoin":false}"""),
        ).andExpect(status().isCreated).andReturn().response.getContentAsString(Charsets.UTF_8)
        return Regex("\"id\":(\\d+)").find(body)!!.groupValues[1].toLong()
    }

    private fun inviteToken(groupId: Long): String =
        jdbcClient.sql("SELECT invite_token FROM groups WHERE id = :id")
            .param("id", groupId).query(String::class.java).single()

    private fun join(groupId: Long, userId: Long) {
        jdbcClient.sql("INSERT INTO group_members (group_id, user_id) VALUES (:g, :u)")
            .param("g", groupId).param("u", userId).update()
    }
}
