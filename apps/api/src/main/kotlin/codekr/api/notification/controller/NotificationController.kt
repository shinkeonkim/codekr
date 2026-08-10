package codekr.api.notification.controller

import codekr.api.auth.security.AuthPrincipal
import codekr.api.common.dto.PageResponse
import codekr.api.notification.dto.NotificationResponse
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

    @GetMapping
    fun findAll(
        @RequestParam(defaultValue = "false") unreadOnly: Boolean,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        principal: AuthPrincipal,
    ): PageResponse<NotificationResponse> =
        notificationService.findPage(
            principal.userId,
            unreadOnly,
            PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, MAX_PAGE_SIZE)),
        )

    @GetMapping("/unread-count")
    fun unreadCount(principal: AuthPrincipal) =
        UnreadCountResponse(notificationService.unreadCount(principal.userId))

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun markRead(@PathVariable id: Long, principal: AuthPrincipal) =
        notificationService.markRead(principal.userId, id)

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun markAllRead(principal: AuthPrincipal) {
        notificationService.markAllRead(principal.userId)
    }
}
