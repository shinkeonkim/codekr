package codekr.api.submission

import codekr.api.auth.security.JwtTokenProvider
import codekr.api.notification.entity.NotificationCategory
import codekr.api.submission.view.SubmissionViewNotifier
import codekr.api.submission.view.SubmissionViewRepository
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals

/** 내 코드를 누가 봤는지 (#136). */
class SubmissionViewIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var jdbcClient: JdbcClient
    @Autowired private lateinit var viewRepository: SubmissionViewRepository
    @Autowired private lateinit var notifier: SubmissionViewNotifier

    private var authorId: Long = 0
    private lateinit var authorToken: String
    private lateinit var viewerToken: String
    private lateinit var adminToken: String
    private var publicSubmission: Long = 0

    @BeforeEach
    fun setUp() {
        val author = userRepository.save(User("author@codekr.dev", "x", "작성자", setOf(UserRole.USER)))
        authorId = author.id
        authorToken = tokenProvider.issueAccessToken(author)
        viewerToken = tokenProvider.issueAccessToken(
            userRepository.save(User("viewer@codekr.dev", "x", "구경꾼", setOf(UserRole.USER))),
        )
        adminToken = tokenProvider.issueAccessToken(
            userRepository.save(User("admin@codekr.dev", "x", "관리자", setOf(UserRole.USER, UserRole.SUPERUSER))),
        )

        jdbcClient.sql(
            """
            INSERT INTO problems (id, slug, title, category, difficulty_level, description, published)
            VALUES (1, 'a', 'A 문제', 'ALGORITHM', 1, '설명', true)
            """,
        ).update()
        publicSubmission = insertSubmission("PUBLIC")
    }

    @Test
    fun `기본은 꺼져 있어 아무것도 기록하지 않는다`() {
        // 열람 기록은 조회자의 프라이버시를 건드린다. 켜져 있는 것이 기본이면
        // "동의 없이 추적" 이 기본이 된다.
        view(publicSubmission, viewerToken)

        assertEquals(0, viewCount())
    }

    @Test
    fun `켜면 코드를 본 사람이 기록된다`() {
        enableNotification()

        view(publicSubmission, viewerToken)

        assertEquals(1, viewCount())
    }

    @Test
    fun `같은 사람이 여러 번 봐도 하루 한 번이다`() {
        enableNotification()

        repeat(5) { view(publicSubmission, viewerToken) }

        // 새로고침이나 뒤로가기가 숫자를 부풀리면 안 된다.
        assertEquals(1, viewCount())
    }

    @Test
    fun `자기 제출을 보는 것은 세지 않는다`() {
        enableNotification()

        view(publicSubmission, authorToken)

        assertEquals(0, viewCount())
    }

    @Test
    fun `어드민 조회는 세지 않는다`() {
        enableNotification()

        // 운영 행위가 알림이 되면 안 된다.
        view(publicSubmission, adminToken)

        assertEquals(0, viewCount())
    }

    @Test
    fun `코드가 안 보이는 제출은 세지 않는다`() {
        enableNotification()
        val private = insertSubmission("PRIVATE")

        // 판정만 보는 것은 목록에서도 보인다. 코드를 읽은 것과 무게가 다르다.
        view(private, viewerToken)

        assertEquals(0, viewCount())
    }

    @Test
    fun `보는 사람에게도 알려진다는 사실을 알린다`() {
        enableNotification()

        mockMvc.perform(get("/api/v1/submissions/" + publicSubmission).header("Authorization", "Bearer " + viewerToken))
            .andExpect(status().isOk)
            // 기록에 남는다면 보기 전에 그 사실을 알아야 한다.
            .andExpect(jsonPath("$.viewNotified").value(true))

        // 작성자 본인에게는 해당 없다.
        mockMvc.perform(get("/api/v1/submissions/" + publicSubmission).header("Authorization", "Bearer " + authorToken))
            .andExpect(jsonPath("$.viewNotified").value(false))
    }

    @Test
    fun `하루치를 묶어 한 번만 알린다`() {
        enableNotification()
        val second = insertSubmission("PUBLIC")
        view(publicSubmission, viewerToken)
        view(second, viewerToken)
        // 어제 날짜로 옮겨 알림 대상이 되게 한다.
        jdbcClient.sql("UPDATE submission_views SET viewed_on = current_date - 1").update()

        notifier.notifyYesterday()

        // 건별로 알리면 알림함이 이것으로만 찬다.
        mockMvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + authorToken))
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].title").value("어제 1명이 내 코드를 봤습니다"))
            .andExpect(jsonPath("$.content[0].categoryLabel").value(NotificationCategory.SUBMISSION_VIEW.label))
    }

    @Test
    fun `오래된 기록은 지운다`() {
        enableNotification()
        view(publicSubmission, viewerToken)
        jdbcClient.sql("UPDATE submission_views SET viewed_on = current_date - 90").update()

        notifier.purgeOld()

        // 알림을 만들고 나면 원자료가 할 일은 끝난다 (ADR-0007).
        assertEquals(0, viewCount())
    }

    private fun enableNotification() {
        mockMvc.perform(
            patch("/api/v1/users/me/settings")
                .header("Authorization", "Bearer " + authorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"viewNotificationEnabled\":true}"),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.viewNotificationEnabled").value(true))
    }

    private fun view(submissionId: Long, token: String) {
        mockMvc.perform(get("/api/v1/submissions/" + submissionId).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk)
    }

    private fun viewCount(): Int =
        jdbcClient.sql("SELECT count(*) FROM submission_views").query(Int::class.java).single()

    private fun insertSubmission(visibility: String): Long =
        jdbcClient.sql(
            """
            INSERT INTO submissions
                (user_id, problem_id, runtime_id, source_code, status, verdict, kind, visibility, total_count)
            VALUES (:userId, 1, 'python:3.12', 'print(3)', 'COMPLETED', 'ACCEPTED', 'USER', :visibility, 1)
            RETURNING id
            """,
        )
            .param("userId", authorId)
            .param("visibility", visibility)
            .query(Long::class.java)
            .single()
}
