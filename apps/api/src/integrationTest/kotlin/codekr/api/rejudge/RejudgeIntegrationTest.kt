package codekr.api.rejudge

import codekr.api.auth.security.JwtTokenProvider
import codekr.api.queue.message.JudgeEventMessage
import codekr.api.rejudge.service.RejudgeService
import codekr.api.submission.entity.Verdict
import codekr.api.submission.repository.SubmissionRepository
import codekr.api.submission.service.JudgeResultRecorder
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 재채점 — 판정 번복과 알림 (#107). */
class RejudgeIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var jdbcClient: JdbcClient
    @Autowired private lateinit var rejudgeService: RejudgeService
    @Autowired private lateinit var submissionRepository: SubmissionRepository
    @Autowired private lateinit var recorder: JudgeResultRecorder

    private var userId: Long = 0
    private var adminId: Long = 0
    private lateinit var token: String
    private lateinit var adminToken: String

    @BeforeEach
    fun setUp() {
        val user = userRepository.save(User("solver@codekr.dev", "x", "풀이왕", setOf(UserRole.USER)))
        userId = user.id
        token = tokenProvider.issueAccessToken(user)

        val admin = userRepository.save(
            User("admin@codekr.dev", "x", "관리자", setOf(UserRole.USER, UserRole.SUPERUSER)),
        )
        adminId = admin.id
        adminToken = tokenProvider.issueAccessToken(admin)

        jdbcClient.sql(
            """
            INSERT INTO problems (id, slug, title, category, difficulty_level, description, published)
            VALUES (1, 'two-sum', '두 수의 합', 'ALGORITHM', 1, '설명', true)
            """,
        ).update()
        jdbcClient.sql(
            """
            INSERT INTO problem_testcases (problem_id, seq, input, expected_output, visibility)
            VALUES (1, 1, '1 2', '3', 'HIDDEN')
            """,
        ).update()
    }

    @Test
    fun `재채점 결과 틀렸으면 오답으로 바꾸고 알린다`() {
        val id = insertSubmission(verdict = "ACCEPTED")

        rejudgeService.rejudgeProblem(1, "테스트케이스가 수정되었습니다", adminId)
        complete(id, Verdict.WRONG_ANSWER)

        // 판정은 나빠지는 방향으로도 바뀐다. 안 그러면 잘못된 판정 위에 통계·랭킹이 쌓인다.
        assertEquals(Verdict.WRONG_ANSWER, submissionRepository.findById(id).get().verdict)

        mockMvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].title").value("재채점으로 판정이 바뀌었습니다"))
            // 왜 바뀌었는지와 **무엇에서 무엇으로** 바뀌었는지가 함께 가야 한다 (#187).
            .andExpect(
                jsonPath("$.content[0].body")
                    .value(
                        org.hamcrest.Matchers.containsString(
                            "테스트케이스가 수정되었습니다 판정이 바뀌었습니다: 맞았습니다 → 틀렸습니다",
                        ),
                    ),
            )
            .andExpect(jsonPath("$.content[0].link").value("/submissions/$id"))
    }

    @Test
    fun `판정이 그대로여도 결과를 알린다`() {
        val id = insertSubmission(verdict = "ACCEPTED")

        rejudgeService.rejudgeProblem(1, "테스트케이스가 수정되었습니다", adminId)
        complete(id, Verdict.ACCEPTED)

        // **재채점 대상이었다는 사실 자체가 알릴 값이다** (#187). 알리지 않으면 자기 결과가
        // 확정된 것인지 아직 도는 중인지 구분할 수 없다.
        mockMvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].title").value("재채점 결과: 판정이 그대로입니다"))
            .andExpect(
                jsonPath("$.content[0].body")
                    .value(
                        org.hamcrest.Matchers.containsString(
                            "테스트케이스가 수정되었습니다 다시 채점했지만 1건 모두 판정이 같습니다.",
                        ),
                    ),
            )
    }

    @Test
    fun `전이를 전부 남긴다`() {
        // 바뀐 것만 남기면 "그때 무슨 일이 있었나" 에 답할 수 없다 (#187).
        val flipped = insertSubmission(verdict = "WRONG_ANSWER")
        val kept = insertSubmission(verdict = "ACCEPTED")

        rejudgeService.rejudgeProblem(1, "이유", adminId)
        complete(flipped, Verdict.ACCEPTED)
        complete(kept, Verdict.ACCEPTED)

        val rows = jdbcClient
            .sql("SELECT previous_verdict, new_verdict FROM rejudge_submission_results ORDER BY submission_id")
            .query { rs, _ -> rs.getString("previous_verdict") to rs.getString("new_verdict") }
            .list()

        assertEquals(listOf("WRONG_ANSWER" to "ACCEPTED", "ACCEPTED" to "ACCEPTED"), rows)
    }

    @Test
    fun `한 사람이 여러 번 냈어도 알림은 한 번이다`() {
        // 스무 번 낸 사람에게 스무 번 울리면 알림함이 못 쓰게 된다.
        val first = insertSubmission(verdict = "WRONG_ANSWER")
        val second = insertSubmission(verdict = "WRONG_ANSWER")

        rejudgeService.rejudgeProblem(1, "이유", adminId)
        complete(first, Verdict.ACCEPTED)
        complete(second, Verdict.ACCEPTED)

        mockMvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(
                jsonPath("$.content[0].body")
                    .value(org.hamcrest.Matchers.containsString("틀렸습니다 → 맞았습니다 (2건)")),
            )
    }

    @Test
    fun `재채점이 끝나면 배치 표시를 지운다`() {
        val id = insertSubmission(verdict = "WRONG_ANSWER")

        rejudgeService.rejudgeProblem(1, "이유", adminId)
        assertTrue(submissionRepository.findById(id).get().isRejudging)

        complete(id, Verdict.ACCEPTED)

        val after = submissionRepository.findById(id).get()
        assertFalse(after.isRejudging)
        assertNull(after.previousVerdict)
    }

    @Test
    fun `정답 검증 제출은 재채점 대상이 아니다`() {
        insertSubmission(verdict = "ACCEPTED", kind = "SOLUTION_VERIFICATION")
        insertSubmission(verdict = "ACCEPTED")

        val response = rejudgeService.rejudgeProblem(1, "이유", adminId)

        assertEquals(1, response.targetCount, "사용자 제출만 대상이어야 합니다")
    }

    @Test
    fun `이유 없이는 재채점할 수 없다`() {
        insertSubmission(verdict = "ACCEPTED")

        // 이유는 사용자에게 그대로 전달되므로 비워 둘 수 없다.
        mockMvc.perform(
            post("/api/v1/admin/problems/1/rejudge")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"reason":""}"""),
        ).andExpect(status().isBadRequest)
    }

    private fun insertSubmission(verdict: String, kind: String = "USER"): Long =
        jdbcClient.sql(
            """
            INSERT INTO submissions (user_id, problem_id, runtime_id, source_code, status, verdict, kind, total_count)
            VALUES (:userId, 1, 'python:3.12', 'print(3)', 'COMPLETED', :verdict, :kind, 1)
            RETURNING id
            """,
        )
            .param("userId", userId)
            .param("verdict", verdict)
            .param("kind", kind)
            .query(Long::class.java)
            .single()

    /** 채점기가 결과를 돌려준 것처럼 흉내낸다. */
    private fun complete(submissionId: Long, verdict: Verdict) {
        recorder.record(
            JudgeEventMessage(
                type = JudgeEventMessage.TYPE_COMPLETED,
                submissionId = submissionId,
                verdict = verdict.name,
                passedCount = if (verdict == Verdict.ACCEPTED) 1 else 0,
                totalCount = 1,
            ),
        )
    }
}
