package codekr.api.user.profile

import codekr.api.audit.entity.AdminAction
import codekr.api.audit.service.AdminAuditService
import codekr.api.auth.security.AuthPrincipal
import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.config.security.AdminApi
import codekr.api.user.entity.UserRole
import codekr.api.user.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 어드민이 소개를 지운다 (#310).
 *
 * **지금 사이트에 신고 기능이 없다.** 그래도 프로필은 로그인 없이 열리는 화면이라,
 * 광고나 사칭이 적히면 지울 길은 있어야 한다. 고쳐 쓰지 않고 **지우기만** 한다 —
 * 남의 글을 대신 고치는 것은 다른 일이다.
 */
@RestController
@RequestMapping("/api/v1/admin/users/{userId}/bio")
class AdminBioController(private val adminBioService: AdminBioService) {

    @DeleteMapping
    @AdminApi(UserRole.ADMIN)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun clear(
        @PathVariable userId: Long,
        @RequestParam reason: String?,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ) = adminBioService.clear(principal.userId, userId, reason)
}

@Service
class AdminBioService(
    private val userRepository: UserRepository,
    private val auditService: AdminAuditService,
) {

    @Transactional
    fun clear(actorId: Long, userId: Long, reason: String?) {
        val user = userRepository.findById(userId).orElseThrow { ApiException(ErrorCode.USER_NOT_FOUND) }

        // 지운 뒤에는 무엇이 적혀 있었는지 알 길이 없다. **기록에 남겨 둔다** (#225).
        val previous = user.bio
        user.bio = null

        auditService.record(
            actorId = actorId,
            action = AdminAction.BIO_CLEAR,
            targetId = userId,
            targetLabel = user.nickname,
            reason = reason,
            detail = previous,
        )
    }
}
