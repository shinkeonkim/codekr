package codekr.api.problem.admin

import codekr.api.audit.entity.AdminAction
import codekr.api.audit.entity.AuditTargetType
import codekr.api.audit.repository.AdminAuditLogRepository
import codekr.api.auth.security.JwtTokenProvider
import codekr.api.problem.repository.ProblemRepository
import codekr.api.support.IntegrationTestBase
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import codekr.api.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 일괄 공개/비공개 (#627).
 *
 * **테스트케이스가 살아남는지**를 함께 본다. 이 경로가 있는 이유가 그것이다 —
 * `PUT /{id}` 는 공개 여부만 바꿔도 테스트케이스를 지웠다 다시 넣는다.
 */
class ProblemPublishIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var problemRepository: ProblemRepository
    @Autowired private lateinit var auditRepository: AdminAuditLogRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider

    private lateinit var adminToken: String
    private var draftA: Long = 0
    private var draftB: Long = 0
    private var opened: Long = 0

    @BeforeEach
    fun setUp() {
        adminToken = tokenProvider.issueAccessToken(
            userRepository.save(User("admin@codekr.dev", "x", "관리자", setOf(UserRole.ADMIN))),
        )
        draftA = create("draft-a", published = false)
        draftB = create("draft-b", published = false)
        opened = create("opened", published = true)
    }

    @Test
    fun `여러 문제를 한 번에 공개한다`() {
        publish(listOf(draftA, draftB), published = true)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.requested").value(2))
            .andExpect(jsonPath("$.changed").value(2))

        assertTrue(problemRepository.findByIdAndDeletedAtIsNull(draftA)!!.published)
        assertTrue(problemRepository.findByIdAndDeletedAtIsNull(draftB)!!.published)
    }

    @Test
    fun `테스트케이스를 다시 쓰지 않는다`() {
        // 이 경로가 있는 이유. `PUT /{id}` 는 공개 여부만 바꿔도 전 행을 지웠다 다시 넣는다.
        // 행 id 로 본다 — 지웠다 다시 넣으면 **번호가 새로 나온다.**
        val before = testcaseIds(draftA)

        publish(listOf(draftA), published = true).andExpect(status().isOk)

        assertEquals(before, testcaseIds(draftA))
        assertEquals(2, before.size)
    }

    @Test
    fun `이미 공개된 것은 세지 않는다`() {
        // "3건을 공개했습니다" 가 맞으려면 **실제로 바뀐 수**를 돌려줘야 한다.
        publish(listOf(draftA, opened), published = true)
            .andExpect(jsonPath("$.requested").value(2))
            .andExpect(jsonPath("$.changed").value(1))
    }

    @Test
    fun `없는 문제를 조용히 넘기지 않는다`() {
        publish(listOf(draftA, 999_999), published = true)
            .andExpect(jsonPath("$.changed").value(1))
            .andExpect(jsonPath("$.missing[0]").value(999_999))
    }

    @Test
    fun `되돌릴 수도 있다`() {
        publish(listOf(opened), published = false).andExpect(jsonPath("$.changed").value(1))
        assertTrue(!problemRepository.findByIdAndDeletedAtIsNull(opened)!!.published)
    }

    @Test
    fun `관리 기록에 문제마다 남는다`() {
        publish(listOf(draftA, draftB), published = true)

        val logs = auditRepository.findAll().filter { it.action == AdminAction.PROBLEM_PUBLISH }
        assertEquals(2, logs.size)
        // 색인이 (target_type, target_id) 라 회원 42번과 문제 42번이 겹치면 안 된다.
        assertTrue(logs.all { it.targetType == AuditTargetType.PROBLEM })
        assertEquals(setOf(draftA, draftB), logs.map { it.targetId }.toSet())
    }

    @Test
    fun `빈 목록은 받지 않는다`() {
        publish(emptyList(), published = true).andExpect(status().isBadRequest)
    }

    private fun testcaseIds(problemId: Long): List<Long> =
        jdbcOfBase.sql("SELECT id FROM problem_testcases WHERE problem_id = :id AND deleted_at IS NULL ORDER BY id")
            .param("id", problemId)
            .query(Long::class.java)
            .list()
            .filterNotNull()

    private fun publish(ids: List<Long>, published: Boolean) =
        mockMvc.perform(
            post("/api/v1/admin/problems/publish")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"ids": [${ids.joinToString(",")}], "published": $published}"""),
        )

    private fun create(slug: String, published: Boolean): Long {
        val response = mockMvc.perform(
            post("/api/v1/admin/problems")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "slug": "$slug", "title": "두 수의 합",
                      "category": "ALGORITHM", "difficulty": "BRONZE_5",
                      "problemKind": "JUDGE_STDIO",
                      "description": "두 정수를 더한다.", "timeLimitMs": 2000, "memoryLimitMb": 256,
                      "published": $published,
                      "testcases": [
                        {"seq": 1, "input": "1 2\n", "expectedOutput": "3\n", "visibility": "PUBLIC"},
                        {"seq": 2, "input": "10 20\n", "expectedOutput": "30\n", "visibility": "HIDDEN"}
                      ],
                      "templates": []
                    }
                    """.trimIndent(),
                ),
        ).andExpect(status().isCreated).andReturn().response.contentAsString

        return Regex("\"id\":(\\d+)").find(response)!!.groupValues[1].toLong()
    }
}
