package codekr.api.admin.controller

import codekr.api.admin.service.DataResetReport
import codekr.api.admin.service.DataResetService
import codekr.api.auth.security.AuthPrincipal
import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.config.security.AdminApi
import codekr.api.user.entity.UserRole
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 데이터 초기화 (#285). **최고 관리자만.**
 *
 * 되돌릴 수 없는 조작이라 두 겹으로 막는다 — 역할(SUPERUSER)과 확인 문구.
 * 화면의 확인 대화상자만으로는 부족하다. 그것은 잘못 눌린 요청을 막지 못한다.
 */
@RestController
@RequestMapping("/api/v1/admin/data")
class AdminDataResetController(private val dataResetService: DataResetService) {

    @AdminApi(UserRole.SUPERUSER)
    @PostMapping("/reset")
    fun reset(@RequestBody request: DataResetRequest, principal: AuthPrincipal): DataResetReport {
        if (request.confirmation != CONFIRMATION) {
            throw ApiException(
                ErrorCode.VALIDATION_ERROR,
                "확인 문구가 다릅니다. \"$CONFIRMATION\" 을 그대로 입력해야 합니다.",
            )
        }
        return dataResetService.reset(actor = principal.email)
    }

    companion object {
        /**
         * 손으로 옮겨 적어야 하는 문구.
         *
         * **한글이고 문장이다.** `RESET` 같은 짧은 영단어는 습관적으로 칠 수 있다.
         */
        const val CONFIRMATION = "문제와 제출을 모두 지웁니다"
    }
}

data class DataResetRequest(val confirmation: String)
