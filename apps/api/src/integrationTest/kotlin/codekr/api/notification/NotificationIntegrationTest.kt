package codekr.api.notification

import codekr.api.auth.security.JwtTokenProvider
import codekr.api.notification.entity.NotificationCategory
import codekr.api.notification.service.NotificationService
import codekr.api.support.IntegrationTestBase
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import codekr.api.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals

/** 웹 내 알림 (#106). */
class NotificationIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var notificationService: NotificationService

    private var userId: Long = 0
    private var otherId: Long = 0
    private lateinit var token: String

    @BeforeEach
    fun setUp() {
        val user = userRepository.save(User("solver@codekr.dev", "x", "풀이왕", setOf(UserRole.USER)))
        userId = user.id
        token = tokenProvider.issueAccessToken(user)
        otherId = userRepository.save(User("other@codekr.dev", "x", "구경꾼", setOf(UserRole.USER))).id
    }

    @Test
    fun `알림을 만들면 목록과 미읽음 수에 잡힌다`() {
        notificationService.notify(userId, NotificationCategory.JUDGE, "재채점되었습니다", link = "/submissions/1")

        mockMvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].title").value("재채점되었습니다"))
            .andExpect(jsonPath("$.content[0].categoryLabel").value("채점"))
            .andExpect(jsonPath("$.content[0].read").value(false))

        mockMvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.unreadCount").value(1))
    }

    @Test
    fun `남의 알림은 보이지 않는다`() {
        notificationService.notify(otherId, NotificationCategory.JUDGE, "남의 알림")

        mockMvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(0))
    }

    @Test
    fun `모두 읽음 처리하면 미읽음이 0 이 된다`() {
        repeat(3) { notificationService.notify(userId, NotificationCategory.JUDGE, "알림 $it") }

        mockMvc.perform(post("/api/v1/notifications/read-all").header("Authorization", "Bearer $token"))
            .andExpect(status().isNoContent)

        mockMvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.unreadCount").value(0))
    }

    @Test
    fun `대량 발송은 받는 사람 수만큼 만든다`() {
        // 수신 거부가 사라졌으므로(#199) 넘긴 사람은 모두 받는다.
        val created = notificationService.notifyAll(
            listOf(userId, otherId),
            NotificationCategory.JUDGE,
            "재채점 결과가 바뀌었습니다",
        )

        assertEquals(2, created)
    }

    @Test
    fun `설정 응답이 카테고리 목록을 알려준다`() {
        mockMvc.perform(get("/api/v1/users/me/settings").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            // 화면이 카테고리 목록을 하드코딩하지 않게 서버가 알려준다.
            .andExpect(jsonPath("$.notificationCategories.length()").value(NotificationCategory.entries.size))
            // 끄는 항목은 사라졌지만 목록은 남아야 한다 — 알림 탭이 이것으로 만들어진다 (#135, #199).
            .andExpect(jsonPath("$.mutedNotificationCategories").doesNotExist())
    }

    @Test
    fun `카테고리 탭으로 거를 수 있다`() {
        notificationService.notify(userId, NotificationCategory.JUDGE, "채점 알림")
        notificationService.notify(userId, NotificationCategory.SYSTEM, "점검 안내")

        mockMvc.perform(
            get("/api/v1/notifications").param("category", "JUDGE")
                .header("Authorization", "Bearer $token"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].title").value("채점 알림"))

        // 전체 탭이 기본이다.
        mockMvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.content.length()").value(2))
    }

    @Test
    fun `탭과 안 읽은 것만이 함께 걸리면 교집합이다`() {
        notificationService.notify(userId, NotificationCategory.JUDGE, "안 읽은 채점")
        notificationService.notify(userId, NotificationCategory.SYSTEM, "안 읽은 점검")
        mockMvc.perform(post("/api/v1/notifications/read-all").param("category", "SYSTEM")
            .header("Authorization", "Bearer $token"))

        mockMvc.perform(
            get("/api/v1/notifications").param("category", "JUDGE").param("unreadOnly", "true")
                .header("Authorization", "Bearer $token"),
        ).andExpect(jsonPath("$.content.length()").value(1))
    }

    @Test
    fun `모두 읽음은 보고 있는 탭만 읽는다`() {
        // 채점 탭에서 눌렀는데 대회 알림까지 읽음이 되면 보지 않은 것을 읽은 것으로
        // 만든 셈이고, 되돌릴 수 없다 (#135).
        notificationService.notify(userId, NotificationCategory.JUDGE, "채점")
        notificationService.notify(userId, NotificationCategory.SYSTEM, "점검")

        mockMvc.perform(
            post("/api/v1/notifications/read-all").param("category", "JUDGE")
                .header("Authorization", "Bearer $token"),
        ).andExpect(status().isNoContent)

        mockMvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.unreadCount").value(1))
            .andExpect(jsonPath("$.byCategory.JUDGE").value(0))
            .andExpect(jsonPath("$.byCategory.SYSTEM").value(1))
    }

    @Test
    fun `전체 탭에서는 전부 읽는다`() {
        notificationService.notify(userId, NotificationCategory.JUDGE, "채점")
        notificationService.notify(userId, NotificationCategory.SYSTEM, "점검")

        mockMvc.perform(post("/api/v1/notifications/read-all").header("Authorization", "Bearer $token"))
            .andExpect(status().isNoContent)

        mockMvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.unreadCount").value(0))
    }

    @Test
    fun `탭별 안 읽은 수는 없는 카테고리도 0 으로 채운다`() {
        // 화면이 빈 값을 다루지 않게 한다.
        mockMvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.byCategory.JUDGE").value(0))
            .andExpect(jsonPath("$.byCategory.CONTEST").value(0))
            .andExpect(jsonPath("$.byCategory.SYSTEM").value(0))
    }

}
