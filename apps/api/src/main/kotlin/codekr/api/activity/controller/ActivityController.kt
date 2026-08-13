package codekr.api.activity.controller

import codekr.api.config.security.AuthenticatedApi
import codekr.api.config.security.PublicApi
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
     * 남의 활동 (#117, #333).
     *
     * **프로필(#83)과 같은 선을 따른다** — 그쪽이 열렸으므로 여기도 열린다. 프로필만
     * 열고 이것을 막으면 페이지의 절반이 비는데, 비로그인은 그것이 원래 비어 있는 것인지
     * 고장인지 알 수 없다.
     *
     * 선은 **"센 숫자냐 한 줄 한 줄이냐"** 로 긋는다. 이것은 날짜별 **개수**라 어떤
     * 문제를 어떤 결과로 냈는지가 없다 — 그 목록(#34)은 지금도 로그인이 필요하다.
     */
    @PublicApi
    @GetMapping("/{nickname}/activity")
    fun findUserActivity(
        @PathVariable nickname: String,
        @RequestParam(required = false) year: Int?,
    ): ActivityResponse {
        // 프로필과 같은 규칙 (#307): handle 로 찾고, 옛 주소(닉네임)도 한 번 더 본다.
        val user = userRepository.findByHandle(nickname)
            ?: userRepository.findByNickname(nickname)
            ?: throw ApiException(ErrorCode.USER_NOT_FOUND)
        return activityService.findActivity(user.id, from = null, to = null, year = year)
    }
}
