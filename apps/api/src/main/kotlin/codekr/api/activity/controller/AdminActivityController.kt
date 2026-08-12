package codekr.api.activity.controller

import codekr.api.user.entity.UserRole
import codekr.api.config.security.AdminApi
import codekr.api.activity.repository.UserDailyActivityRepository
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 활동 집계 재계산 (#105).
 *
 * **집계를 저장하기로 한 이상 이 경로가 반드시 있어야 한다.** 값이 어긋났을 때 되돌릴
 * 방법이 없으면 애초에 저장하면 안 되는 것이다.
 *
 * 경로 규칙에 따로 적지 않았으므로 최고 관리자만 부를 수 있다 (#103).
 */
@RestController
@RequestMapping("/api/v1/admin/users")
class AdminActivityController(private val activityRepository: UserDailyActivityRepository) {

    @AdminApi(UserRole.SUPERUSER)
    @PostMapping("/{userId}/activity/recompute")
    fun recompute(@PathVariable userId: Long): Map<String, Int> =
        mapOf("days" to activityRepository.recomputeAll(userId))
}
