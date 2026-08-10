package codekr.api.auth.withdrawal

import codekr.api.auth.security.AuthPrincipal
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** 회원 탈퇴 (#140). */
@RestController
class WithdrawalController(private val withdrawalService: WithdrawalService) {

    /** 본인 탈퇴. **되돌릴 수 없다** — 유예 기간을 두지 않았다. */
    @DeleteMapping("/api/v1/users/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun withdraw(principal: AuthPrincipal) = withdrawalService.withdraw(principal.userId)

    /**
     * 어드민의 강제 탈퇴 (#140).
     *
     * 본인 탈퇴와 **같은 경로**를 쓴다 — 두 벌로 두면 한쪽만 고치는 일이 생긴다.
     */
    @DeleteMapping("/api/v1/admin/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun forceWithdraw(@PathVariable id: Long) = withdrawalService.withdraw(id)
}
