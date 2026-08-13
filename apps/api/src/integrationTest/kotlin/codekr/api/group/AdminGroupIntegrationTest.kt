package codekr.api.group

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
 * 어드민이 그룹을 내린다 (#438).
 *
 * **그룹은 누구나 만들고 이름도 아무거나 쓸 수 있다** (#401). 사칭을 구조적으로 막지
 * 않기로 했으므로 내리는 길이 있어야 한다 — 문제가 되는 그룹의 방장이 스스로 해산할
 * 이유는 없다.
 */
class AdminGroupIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var jdbcClient: JdbcClient

    private var adminToken: String = ""
    private var owner: Long = 0
    private var member: Long = 0
    private var groupId: Long = 0

    @BeforeEach
    fun setUp() {
        val admin = userRepository.save(User("admin@codekr.dev", "x", "운영자", setOf(UserRole.USER, UserRole.ADMIN)))
        adminToken = tokenProvider.issueAccessToken(admin)
        owner = userRepository.save(User("owner@codekr.dev", "x", "방장", setOf(UserRole.USER))).id
        member = userRepository.save(User("member@codekr.dev", "x", "멤버", setOf(UserRole.USER))).id
        groupId = group("서울대학교 공식 스터디")
        join(groupId, owner)
        join(groupId, member)
    }

    @Test
    fun `목록에 이름과 방장과 인원이 온다`() {
        // 내릴지 판단하는 데 필요한 것까지다. **명단은 여기서도 보이지 않는다** (#401).
        mockMvc.perform(get("/api/v1/admin/groups").header("Authorization", "Bearer $adminToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].name").value("서울대학교 공식 스터디"))
            .andExpect(jsonPath("$.content[0].ownerNickname").value("방장"))
            .andExpect(jsonPath("$.content[0].memberCount").value(2))
    }

    @Test
    fun `이름으로 찾는다`() {
        group("다른 스터디").also { join(it, owner) }

        mockMvc.perform(
            get("/api/v1/admin/groups").param("q", "서울대").header("Authorization", "Bearer $adminToken"),
        )
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].name").value("서울대학교 공식 스터디"))
    }

    @Test
    fun `내리면 멤버 전원에게 사유가 간다`() {
        /*
          **말없이 사라지면 고칠 수도 없다** (#208). 그리고 방장에게만 알리지 않는다 —
          그룹은 방장의 것이 아니라 그 안 사람들의 것이다.
        */
        mockMvc.perform(
            post("/api/v1/admin/groups/$groupId/takedown")
                .param("reason", "학교를 사칭했습니다")
                .header("Authorization", "Bearer $adminToken"),
        ).andExpect(status().isNoContent)

        val notified = jdbcClient.sql(
            "SELECT count(*) FROM notifications WHERE body LIKE '%학교를 사칭했습니다%'",
        ).query(Int::class.java).single()
        assert(notified == 2) { "멤버 둘 모두에게 가야 한다: ${notified}건" }
    }

    @Test
    fun `내린 그룹은 사라지고 링크도 죽는다`() {
        val token = jdbcClient.sql("SELECT invite_token FROM groups WHERE id = :id")
            .param("id", groupId).query(String::class.java).single()

        mockMvc.perform(
            post("/api/v1/admin/groups/$groupId/takedown")
                .param("reason", "사칭")
                .header("Authorization", "Bearer $adminToken"),
        ).andExpect(status().isNoContent)

        val ownerToken = tokenProvider.issueAccessToken(userRepository.findById(owner).get())
        mockMvc.perform(get("/api/v1/groups/$groupId").header("Authorization", "Bearer $ownerToken"))
            .andExpect(status().isNotFound)
        mockMvc.perform(get("/api/v1/groups/invites/$token").header("Authorization", "Bearer $ownerToken"))
            .andExpect(status().isNotFound)
        mockMvc.perform(get("/api/v1/admin/groups").header("Authorization", "Bearer $adminToken"))
            .andExpect(jsonPath("$.totalElements").value(0))
    }

    @Test
    fun `행을 지우지 않는다`() {
        // 그룹 랭킹(#402)이 이 id 를 가리킨다. 지우면 무엇이었는지 아무도 모른다 (ADR-0007).
        mockMvc.perform(
            post("/api/v1/admin/groups/$groupId/takedown")
                .param("reason", "사칭")
                .header("Authorization", "Bearer $adminToken"),
        ).andExpect(status().isNoContent)

        val rows = jdbcClient.sql("SELECT count(*) FROM groups WHERE id = :id AND deleted_at IS NOT NULL")
            .param("id", groupId).query(Int::class.java).single()
        assert(rows == 1) { "행은 남고 내려간 표시만 있어야 한다" }
    }

    @Test
    fun `사유 없이는 내릴 수 없다`() {
        // 멤버 전원에게 그대로 전해지는 값이다. 빈 사유는 아무 말도 하지 않는다.
        mockMvc.perform(
            post("/api/v1/admin/groups/$groupId/takedown")
                .param("reason", "  ")
                .header("Authorization", "Bearer $adminToken"),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `어드민이 아니면 볼 수도 내릴 수도 없다`() {
        val ownerToken = tokenProvider.issueAccessToken(userRepository.findById(owner).get())

        mockMvc.perform(get("/api/v1/admin/groups").header("Authorization", "Bearer $ownerToken"))
            .andExpect(status().isForbidden)
        mockMvc.perform(
            post("/api/v1/admin/groups/$groupId/takedown")
                .param("reason", "내가 내린다")
                .header("Authorization", "Bearer $ownerToken"),
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `관리 기록에 남는다`() {
        mockMvc.perform(
            post("/api/v1/admin/groups/$groupId/takedown")
                .param("reason", "사칭")
                .header("Authorization", "Bearer $adminToken"),
        ).andExpect(status().isNoContent)

        val logged = jdbcClient.sql(
            "SELECT count(*) FROM admin_audit_logs WHERE action = 'GROUP_TAKEDOWN' AND reason = '사칭'",
        ).query(Int::class.java).single()
        assert(logged == 1) { "누가 왜 내렸는지가 남아야 한다 (#225)" }
    }

    private fun group(name: String): Long =
        jdbcClient.sql(
            """
            INSERT INTO groups (name, description, owner_id, invite_token)
            VALUES (:n, '설명', :o, md5(:n)) RETURNING id
            """,
        ).param("n", name).param("o", owner).query(Long::class.java).single()

    private fun join(groupId: Long, userId: Long) {
        jdbcClient.sql("INSERT INTO group_members (group_id, user_id) VALUES (:g, :u)")
            .param("g", groupId).param("u", userId).update()
    }
}
