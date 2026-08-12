package codekr.api.user

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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 회원 정지 (#224).
 *
 * **지금까지 할 수 있는 일은 "그대로 두기" 와 "되돌릴 수 없이 지우기" 둘뿐이었다.**
 * 여기서 확인하는 것은 그 사이에 놓인 조치가 실제로 막고, 저절로 풀리고, 왜 막혔는지
 * 알려 주는가다.
 */
class UserSuspensionIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var jdbcClient: JdbcClient

    private lateinit var adminToken: String
    private lateinit var memberToken: String
    private var memberId: Long = 0

    @BeforeEach
    fun setUp() {
        adminToken = tokenProvider.issueAccessToken(
            userRepository.save(User("admin@codekr.dev", "x", "관리자", setOf(UserRole.ADMIN))),
        )
        val member = userRepository.save(User("member@codekr.dev", "x", "글쓴이", setOf(UserRole.USER)))
        memberId = member.id
        memberToken = tokenProvider.issueAccessToken(member)
    }

    @Test
    fun `쓰기 정지는 글을 막고 이유와 기한을 알린다`() {
        suspend("WRITE", days = 7)

        // **말없이 실패하면 고장으로 보인다** — 사유와 언제 풀리는지가 응답에 있어야 한다.
        mockMvc.perform(
            post("/api/v1/posts").header("Authorization", "Bearer $memberToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(POST_BODY),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("SUSPENDED"))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("스팸")))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("까지")))
    }

    @Test
    fun `쓰기 정지는 읽기를 막지 않는다`() {
        // 읽기를 막는 것은 **막는 시늉**이다 — 로그아웃하면 그대로 보인다. 게다가
        // 자기가 왜 막혔는지 볼 길까지 없앤다.
        suspend("WRITE", days = 7)

        mockMvc.perform(get("/api/v1/posts").header("Authorization", "Bearer $memberToken"))
            .andExpect(status().isOk)
    }

    @Test
    fun `쓰기 정지는 제출을 막지 않는다`() {
        // 댓글 스팸 때문에 문제 풀이까지 막을 이유가 없다. 이 갈림이 이 이슈의 핵심이다.
        suspend("WRITE", days = 7)

        // 문제가 없어 404 가 나지만, 403(SUSPENDED)이 아니라는 것이 확인하려는 것이다.
        mockMvc.perform(
            post("/api/v1/problems/없는문제/submissions").header("Authorization", "Bearer $memberToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(SUBMIT_BODY),
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `제출 정지는 제출만 막는다`() {
        suspend("SUBMIT", days = 3)

        mockMvc.perform(
            post("/api/v1/problems/없는문제/submissions").header("Authorization", "Bearer $memberToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(SUBMIT_BODY),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("SUSPENDED"))
    }

    @Test
    fun `기한이 지나면 저절로 풀린다`() {
        // 푸는 것을 사람이 기억해야 하면 영구 정지와 같아진다. 기한을 이미 지난 값으로
        // 넣어, 아무도 손대지 않아도 통과하는지 본다.
        val id = suspend("WRITE", days = 1)
        expire(id)

        mockMvc.perform(
            post("/api/v1/posts").header("Authorization", "Bearer $memberToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(POST_BODY),
        )
            .andExpect(status().isCreated)
    }

    @Test
    fun `어드민이 미리 풀 수 있다`() {
        val id = suspend("WRITE", days = 30)

        mockMvc.perform(
            delete("/api/v1/admin/users/$memberId/suspensions/$id")
                .header("Authorization", "Bearer $adminToken"),
        ).andExpect(status().isNoContent)

        mockMvc.perform(
            post("/api/v1/posts").header("Authorization", "Bearer $memberToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(POST_BODY),
        )
            .andExpect(status().isCreated)
    }

    @Test
    fun `정지 중에도 로그아웃과 탈퇴는 된다`() {
        // 정지가 **떠날 자유까지** 뺏지는 않는다.
        suspend("ALL", days = null)

        mockMvc.perform(delete("/api/v1/users/me").header("Authorization", "Bearer $memberToken"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `사유 없이는 정지할 수 없다`() {
        mockMvc.perform(
            post("/api/v1/admin/users/$memberId/suspensions")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"scope":"WRITE","reason":"  ","days":7}"""),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `목록과 상세에 정지가 보인다`() {
        suspend("WRITE", days = 7)

        mockMvc.perform(
            get("/api/v1/admin/users").param("q", "글쓴이").header("Authorization", "Bearer $adminToken"),
        )
            // 상세를 하나씩 열어 봐야 안다면 이미 정지된 사람을 또 정지시킨다.
            .andExpect(jsonPath("$.content[0].suspendedScopes[0]").value("쓰기"))

        mockMvc.perform(
            get("/api/v1/admin/users/$memberId").header("Authorization", "Bearer $adminToken"),
        )
            .andExpect(jsonPath("$.suspensions[0].reason").value("스팸 댓글"))
    }

    @Test
    fun `정지되면 본인에게 알림이 간다`() {
        // 막혔을 때만 보이게 두면 긴 글을 다 쓴 뒤에야 알게 된다. 알림은 끌 수 없으므로
        // (#199) 반드시 닿는다.
        suspend("WRITE", days = 7)

        mockMvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer $memberToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].category").value("SYSTEM"))
            .andExpect(jsonPath("$.content[0].body").value(org.hamcrest.Matchers.containsString("스팸 댓글")))
    }

    @Test
    fun `정지도 관리 기록에 남는다`() {
        suspend("WRITE", days = 7)

        val superuser = tokenProvider.issueAccessToken(
            userRepository.save(User("root@codekr.dev", "x", "최고관리자", setOf(UserRole.SUPERUSER))),
        )
        mockMvc.perform(
            get("/api/v1/admin/audit-logs").param("targetUserId", memberId.toString())
                .header("Authorization", "Bearer $superuser"),
        )
            .andExpect(jsonPath("$.content[0].action").value("SUSPEND"))
            .andExpect(jsonPath("$.content[0].reason").value("스팸 댓글"))
    }

    private companion object {
        const val POST_BODY = """{"board":"FREE","title":"제목","body":"내용"}"""
        const val SUBMIT_BODY = """{"runtimeId":"python-3.12","sourceCode":"print(1)"}"""
    }

    private fun suspend(scope: String, days: Int?): Long {
        val body = mockMvc.perform(
            post("/api/v1/admin/users/$memberId/suspensions")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"scope":"$scope","reason":"스팸 댓글"${days?.let { ",\"days\":$it" } ?: ""}}"""),
        )
            .andExpect(status().isCreated)
            .andReturn().response.contentAsString
        return Regex("\"id\":(\\d+)").find(body)!!.groupValues[1].toLong()
    }

    /** 기한을 이미 지난 값으로 밀어 둔다 — 시간을 기다리지 않고 만료를 재현한다. */
    private fun expire(suspensionId: Long) {
        jdbcClient.sql("UPDATE user_suspensions SET ends_at = now() - interval '1 day' WHERE id = :id")
            .param("id", suspensionId)
            .update()
    }
}
