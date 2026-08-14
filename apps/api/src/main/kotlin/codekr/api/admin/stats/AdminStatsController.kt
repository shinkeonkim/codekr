package codekr.api.admin.stats

import codekr.api.config.security.AdminApi
import codekr.api.user.entity.UserRole
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 어드민 통계 (#550).
 *
 * **읽기만 한다.** 그래서 `ADMIN` 이면 된다 — 역할을 바꾸거나 계정을 지우는 것과 달리
 * 되돌릴 것이 없다 (#103 이 좁힌 것은 쓰는 쪽이다).
 *
 * 다만 **아무나 볼 것은 아니다.** 가입 추세·판정 분포는 우리가 얼마나 아픈지를 그대로
 * 드러낸다.
 */
@Validated
@RestController
@RequestMapping("/api/v1/admin/stats")
class AdminStatsController(private val service: AdminStatsService) {

    @AdminApi(UserRole.ADMIN)
    @GetMapping
    fun overview(
        /**
         * 며칠치를 볼지. **상한을 둔다** — `submissions` 를 기간으로 자르는 것이 이
         * 질의가 감당 가능한 유일한 이유다 (#105).
         */
        @RequestParam(defaultValue = "30") @Min(7) @Max(90) days: Int,
    ): AdminStatsResponse = service.overview(days)
}
