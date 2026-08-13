package codekr.api.user.email

import codekr.api.auth.email.EmailVerificationService
import codekr.api.auth.security.AuthPrincipal
import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.config.security.AuthenticatedApi
import codekr.api.user.repository.UserRepository
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/**
 * 확인한 메일 주소 여러 개 (#396, #240 1단계).
 *
 * **소속 인증이 여기에 통째로 기댄다.** 학교·회사 메일을 확인해야 소속이 붙는데,
 * 로그인 주소를 그것으로 바꾸게 하면 **졸업하는 순간 로그인을 잃는다.**
 */
@RestController
@RequestMapping("/api/v1/users/me/emails")
class UserEmailController(private val service: UserEmailService) {

    @AuthenticatedApi
    @GetMapping
    fun list(principal: AuthPrincipal): List<UserEmailResponse> = service.list(principal.userId)

    /**
     * 주소를 더한다 — **바로 붙지 않고 확인 메일이 간다.**
     *
     * 확인하지 않으면 `@snu.ac.kr` 이라고 적기만 하면 서울대가 된다 (#240 이 그것을
     * 이 이슈의 선행으로 둔 이유다).
     */
    @AuthenticatedApi
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun add(@Valid @RequestBody request: AddEmailRequest, principal: AuthPrincipal) =
        service.requestVerification(principal.userId, request.email.trim().lowercase())

    @AuthenticatedApi
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun remove(@PathVariable id: Long, principal: AuthPrincipal) = service.remove(principal.userId, id)
}

data class AddEmailRequest(
    @field:NotBlank(message = "메일 주소를 입력해 주세요.")
    @field:Email(message = "메일 주소 형식이 아닙니다.")
    val email: String = "",
)

data class UserEmailResponse(val id: Long, val email: String, val verifiedAt: Instant)

@Service
class UserEmailService(
    private val emails: UserEmailRepository,
    private val users: UserRepository,
    private val verificationService: EmailVerificationService,
) {

    fun list(userId: Long): List<UserEmailResponse> =
        emails.findByUserIdOrderByIdAsc(userId).map { UserEmailResponse(it.id, it.email, it.verifiedAt) }

    /**
     * 그 주소로 확인 메일을 보낸다.
     *
     * **이미 누가 쓰는 주소면 거절한다.** 그러지 않으면 한 학교 메일로 두 계정이 같은
     * 소속을 얻는다. 로그인 주소도 함께 본다 — 남의 로그인 주소를 내 추가 주소로
     * 확인해 버리면 그 사람의 소속을 가져가는 셈이다.
     */
    @Transactional
    fun requestVerification(userId: Long, email: String) {
        if (users.findByEmail(email) != null || emails.existsByEmail(email)) {
            throw ApiException(ErrorCode.EMAIL_ALREADY_EXISTS)
        }
        // 발송 상한·쿨다운은 인증 서비스가 이미 센다 (#233). 두 벌로 만들지 않는다.
        verificationService.send(userId, email, enforceCooldown = true, forAddress = email)
    }

    /**
     * 뗀다.
     *
     * **남의 것은 못 뗀다.** 그리고 소속(#398)이 이 주소에 붙으므로, 떼면 그 소속도
     * 함께 떨어져야 한다 — 그것은 소속을 만드는 이슈에서 이 자리에 붙인다.
     */
    @Transactional
    fun remove(userId: Long, id: Long) {
        val target = emails.findById(id).orElseThrow { ApiException(ErrorCode.USER_NOT_FOUND) }
        if (target.userId != userId) throw ApiException(ErrorCode.FORBIDDEN)
        emails.delete(target)
    }
}
