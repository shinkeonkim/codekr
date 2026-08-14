package codekr.api.user.controller

import codekr.api.auth.security.AuthPrincipal
import codekr.api.config.security.AdminApi
import codekr.api.user.dto.AdminEmailVerificationResponse
import codekr.api.user.entity.UserRole
import codekr.api.user.service.AdminEmailVerificationService
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 어드민이 회원의 이메일 인증에 손대는 길 (#524).
 *
 * **둘의 무게가 다르므로 역할도 다르다** — 다시 보내는 것은 아무것도 바꾸지 않지만,
 * 강제 인증은 **확인을 건너뛴다.** #103 이 역할 부여를 `SUPERUSER` 로 좁힌 것과 같은
 * 자리다. 경로로 나누면 순서에 의존해 조용히 덮이므로 핸들러마다 선언한다 (#198).
 */
@RestController
@RequestMapping("/api/v1/admin/users/{id}/email-verification")
class AdminEmailVerificationController(private val service: AdminEmailVerificationService) {

    /** 다시 보낸다. 아무것도 바꾸지 않으므로 `ADMIN` 이면 된다. */
    @AdminApi(UserRole.ADMIN)
    @PostMapping("/resend")
    fun resend(@PathVariable id: Long, principal: AuthPrincipal): AdminEmailVerificationResponse =
        service.resend(principal.userId, id)

    /**
     * 확인 없이 인증 처리한다. **`SUPERUSER` 만** 할 수 있고 사유가 필수다.
     *
     * 사유를 검사하는 곳은 여기가 아니라 기록 쪽이다 (#225) — 규칙이 두 곳에 있으면
     * 갈라진다.
     */
    @AdminApi(UserRole.SUPERUSER)
    @PostMapping
    fun forceVerify(
        @PathVariable id: Long,
        @RequestBody request: ForceVerifyRequest,
        principal: AuthPrincipal,
    ): AdminEmailVerificationResponse = service.forceVerify(principal.userId, id, request.reason)
}

/** 강제 인증 요청. 사유만 받는다 — 무엇을 인증할지는 경로가 정한다. */
data class ForceVerifyRequest(val reason: String? = null)
