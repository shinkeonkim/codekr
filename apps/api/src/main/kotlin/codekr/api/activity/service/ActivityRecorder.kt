package codekr.api.activity.service

import codekr.api.activity.ActivityPolicy
import codekr.api.activity.repository.UserDailyActivityRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 채점이 끝난 제출을 일별 활동에 반영한다 (#105).
 *
 * **다시 세어 덮어쓰는 방식이라 몇 번 불려도 결과가 같다.** 재채점(#107)은 같은 제출을
 * 다시 COMPLETED 로 만드는데, 증분이었다면 그때마다 활동이 늘어난다.
 */
@Component
class ActivityRecorder(private val activityRepository: UserDailyActivityRepository) {

    @Transactional
    fun recordCompletion(userId: Long, submittedAt: Instant) {
        activityRepository.refreshDay(userId, submittedAt.atZone(ActivityPolicy.ZONE).toLocalDate())
    }
}
