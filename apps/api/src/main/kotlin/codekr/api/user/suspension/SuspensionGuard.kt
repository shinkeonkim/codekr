package codekr.api.user.suspension

import codekr.api.auth.security.AuthPrincipal
import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 정지된 회원의 쓰기·제출을 막는다 (#224).
 *
 * **탈퇴처럼 토큰을 무효화하지 않는다.** 탈퇴는 되돌릴 수 없는 한 번이라 Redis 에
 * 표시해 두고 끝이지만(#140), 정지는 기한이 있고 중간에 풀릴 수 있고 범위가 있다 —
 * 토큰에 담을 수 없는 것들이다. 그래서 **쓰기·제출 요청에서만** 표를 읽는다.
 * 읽기 요청에는 질의가 붙지 않으므로 대부분의 트래픽은 그대로다.
 */
@Component
class SuspensionGuard(
    private val suspensions: UserSuspensionRepository,
    private val clock: Clock,
) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val action = SuspendedAction.of(request.method, request.requestURI)
        if (action == SuspendedAction.NONE) return true

        val userId = currentUserId() ?: return true
        val now = clock.instant()
        val scope = if (action == SuspendedAction.SUBMIT) SuspensionScope.SUBMIT else SuspensionScope.WRITE

        suspensions.findActive(userId, now)
            .firstOrNull { it.scope.covers(scope) }
            ?.let { throw ApiException(ErrorCode.SUSPENDED, describe(it)) }

        return true
    }

    private fun currentUserId(): Long? =
        (SecurityContextHolder.getContext().authentication?.principal as? AuthPrincipal)?.userId

    /**
     * **말없이 실패하면 고장으로 보인다.** 왜 막혔는지와 언제 풀리는지를 함께 준다 —
     * 그 둘이 없으면 문의밖에 할 것이 없다.
     */
    private fun describe(suspension: UserSuspension): String {
        val until = suspension.endsAt?.let { "${format(it)} 까지" } ?: "기한 없이"
        return "${suspension.scope.label}가 $until 제한되었습니다. 사유: ${suspension.reason}"
    }

    private fun format(at: Instant): String = FORMAT.format(at.atZone(ZONE))

    private companion object {
        val ZONE: ZoneId = ZoneId.of("Asia/Seoul")
        val FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}
