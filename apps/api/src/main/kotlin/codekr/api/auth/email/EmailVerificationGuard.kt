package codekr.api.auth.email

import codekr.api.auth.security.AuthPrincipal
import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.user.repository.UserRepository
import codekr.api.user.suspension.SuspendedAction
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

/**
 * 확인하지 않은 주소로는 글을 쓸 수 없다 (#233).
 *
 * **막는 것은 쓰기까지다.** 로그인을 막으면 메일이 안 온 사람이 재발송을 누를 화면조차
 * 열지 못하고, 제출까지 막으면 문제를 푸는 것과 주소 확인이 아무 상관없는데 묶인다.
 *
 * **보낼 수 없을 때는 요구하지도 않는다.** 메일 설정이 없는 환경(로컬·미리보기)에서
 * 인증을 요구하면 아무도 인증할 수 없는 계정이 만들어진다 — 요구는 보낼 수 있을 때만
 * 뜻이 있다.
 *
 * 무엇이 "쓰기" 인지는 `SuspendedAction`(#224)이 이미 정해 두었다. 같은 판정을 두 번
 * 적으면 언젠가 갈린다.
 */
@Component
class EmailVerificationGuard(
    private val userRepository: UserRepository,
    private val mailSender: MailSender,
) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (!mailSender.enabled) return true
        if (SuspendedAction.of(request.method, request.requestURI) != SuspendedAction.WRITE) return true

        val principal = SecurityContextHolder.getContext().authentication?.principal as? AuthPrincipal
            ?: return true

        val verified = userRepository.findById(principal.userId)
            .map { it.emailVerifiedAt != null }
            .orElse(true)
        if (!verified) {
            throw ApiException(
                ErrorCode.EMAIL_NOT_VERIFIED,
                "가입할 때 보낸 메일의 링크를 눌러 주소를 확인해 주세요. 설정에서 다시 받을 수 있습니다.",
            )
        }
        return true
    }
}
