package codekr.api.activity.controller

import codekr.api.config.security.AuthenticatedApi
import codekr.api.activity.dto.ActivityResponse
import codekr.api.activity.service.ActivityService
import codekr.api.auth.security.AuthPrincipal
import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.user.repository.UserRepository
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/users")
class ActivityController(
    private val activityService: ActivityService,
    private val userRepository: UserRepository,
) {

    @AuthenticatedApi
    @GetMapping("/me/activity")
    fun findMyActivity(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        /** 그 해 전체를 본다 (#81). from/to 보다 우선한다. */
        @RequestParam(required = false) year: Int?,
        principal: AuthPrincipal,
    ): ActivityResponse = activityService.findActivity(principal.userId, from, to, year)

    /**
     * 남의 활동 (#117).
     *
     * 프로필(#83)과 같은 선을 따른다 — 로그인이 필요하고, 전체 제출 목록에 이미 담긴
     * 정보를 날짜별로 묶은 것이다. 프로필만 열고 활동을 막으면 그게 우회로가 된다.
     */
    @AuthenticatedApi
    @GetMapping("/{nickname}/activity")
    fun findUserActivity(
        @PathVariable nickname: String,
        @RequestParam(required = false) year: Int?,
    ): ActivityResponse {
        val user = userRepository.findByNickname(nickname) ?: throw ApiException(ErrorCode.USER_NOT_FOUND)
        return activityService.findActivity(user.id, from = null, to = null, year = year)
    }
}
