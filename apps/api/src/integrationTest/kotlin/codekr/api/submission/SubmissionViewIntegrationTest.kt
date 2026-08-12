package codekr.api.submission

import codekr.api.auth.security.JwtTokenProvider
import codekr.api.notification.entity.NotificationCategory
import codekr.api.submission.view.SubmissionViewNotifier
import codekr.api.submission.view.SubmissionViewRepository
import codekr.api.support.IntegrationTestBase
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import codekr.api.user.repository.UserRepository
import java.time.Clock
import java.time.LocalDate
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
    @Autowired private lateinit var clock: Clock
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
    fun `설정과 무관하게 기록한다`() {
        // **끄는 수단이 사라졌다** (#199). 전에는 작성자가 켜 둔 경우에만 기록했고,
        // 기본이 꺼짐이라 사실상 아무것도 남지 않았다.
        //
        // 조회자에게는 보기 전에 그 사실을 알린다(`viewNotified`) — 알리는 것과
        // 기록하는 것의 조건이 같아야 안내가 거짓말이 되지 않는다.
        view(publicSubmission, viewerToken)

        assertEquals(1, viewCount())
    }

    @Test
    fun `코드를 본 사람이 기록된다`() {

        view(publicSubmission, viewerToken)

        assertEquals(1, viewCount())
    }

    @Test
    fun `같은 사람이 여러 번 봐도 하루 한 번이다`() {

        repeat(5) { view(publicSubmission, viewerToken) }

        // 새로고침이나 뒤로가기가 숫자를 부풀리면 안 된다.
        assertEquals(1, viewCount())
    }

    @Test
    fun `자기 제출을 보는 것은 세지 않는다`() {

        view(publicSubmission, authorToken)

        assertEquals(0, viewCount())
    }

    @Test
    fun `어드민 조회는 세지 않는다`() {

        // 운영 행위가 알림이 되면 안 된다.
        view(publicSubmission, adminToken)

        assertEquals(0, viewCount())
    }

    @Test
    fun `코드가 안 보이는 제출은 세지 않는다`() {
        val private = insertSubmission("PRIVATE")

        // 판정만 보는 것은 목록에서도 보인다. 코드를 읽은 것과 무게가 다르다.
        view(private, viewerToken)

        assertEquals(0, viewCount())
    }

    @Test
    fun `보는 사람에게도 알려진다는 사실을 알린다`() {

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
        val second = insertSubmission("PUBLIC")
        view(publicSubmission, viewerToken)
        view(second, viewerToken)
        // 어제 날짜로 옮겨 알림 대상이 되게 한다.
        moveViewsTo(seoulToday().minusDays(1))

        notifier.notifyYesterday()

        // 건별로 알리면 알림함이 이것으로만 찬다.
        mockMvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + authorToken))
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].title").value("어제 1명이 내 코드를 봤습니다"))
            .andExpect(jsonPath("$.content[0].categoryLabel").value(NotificationCategory.SUBMISSION_VIEW.label))
    }

    @Test
    fun `오래된 기록은 지운다`() {
        view(publicSubmission, viewerToken)
        moveViewsTo(seoulToday().minusDays(90))

        notifier.purgeOld()

        // 알림을 만들고 나면 원자료가 할 일은 끝난다 (ADR-0007).
        assertEquals(0, viewCount())
    }

    /**
     * 열람 기록을 특정 날짜로 옮긴다 (#241).
     *
     * **`current_date` 를 쓰지 않는다.** PostgreSQL JDBC 드라이버는 세션 시간대를 클라이언트
     * JVM 의 기본 시간대로 맞추므로, `current_date` 는 시험이 도는 기계의 시간대를 따른다.
     * 앱은 서울 날짜로 찾는데(`SubmissionViewNotifier`), 러너가 UTC 면 서울 자정~오전 9시
     * 사이에 하루가 어긋나 시험이 **하루 중 언제 도는지에 따라** 깨졌다.
     */
    private fun moveViewsTo(day: LocalDate) {
        jdbcClient.sql("UPDATE submission_views SET viewed_on = :day").param("day", day).update()
    }

    /**
     * 오늘. **앱이 보는 시계를 그대로 본다** (#241).
     *
     * 시간대를 여기에 다시 적지 않는다 — 적는 순간 앱과 시험이 각자의 기준을 갖게 되고,
     * 그것이 이 시험을 깨뜨린 원인이었다.
     */
    private fun seoulToday(): LocalDate = LocalDate.now(clock)


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
