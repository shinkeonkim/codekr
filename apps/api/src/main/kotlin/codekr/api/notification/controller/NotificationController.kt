package codekr.api.notification.controller

import codekr.api.config.security.AuthenticatedApi
import codekr.api.auth.security.AuthPrincipal
import codekr.api.common.dto.PageResponse
import codekr.api.notification.dto.NotificationResponse
import codekr.api.notification.entity.NotificationCategory
import codekr.api.notification.dto.UnreadCountResponse
import codekr.api.notification.service.NotificationService
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

private const val MAX_PAGE_SIZE = 50

/** 내 알림 (#106). 남의 알림은 경로에 지정할 방법이 없다. */
@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(private val notificationService: NotificationService) {

    @AuthenticatedApi
    @GetMapping
    fun findAll(
        @RequestParam(defaultValue = "false") unreadOnly: Boolean,
        /** 비우면 전체 탭이다. 탭 목록은 설정 응답의 카테고리 옵션에서 만든다 (#106). */
        @RequestParam(required = false) category: NotificationCategory?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        principal: AuthPrincipal,
    ): PageResponse<NotificationResponse> =
        notificationService.findPage(
            principal.userId,
            unreadOnly,
            category,
            PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, MAX_PAGE_SIZE)),
        )

    @AuthenticatedApi
    @GetMapping("/unread-count")
    fun unreadCount(principal: AuthPrincipal) = UnreadCountResponse(
        notificationService.unreadCount(principal.userId),
        notificationService.unreadCountByCategory(principal.userId),
    )

    @AuthenticatedApi
    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun markRead(@PathVariable id: Long, principal: AuthPrincipal) =
        notificationService.markRead(principal.userId, id)

    @AuthenticatedApi
    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun markAllRead(
        @RequestParam(required = false) category: NotificationCategory?,
        principal: AuthPrincipal,
    ) {
        // 보고 있는 탭만 읽는다. 전체 탭에서만 전부 읽는다 (#135).
        notificationService.markAllRead(principal.userId, category)
    }
}
