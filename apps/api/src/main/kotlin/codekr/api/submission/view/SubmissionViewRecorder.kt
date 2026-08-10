package codekr.api.submission.view

import codekr.api.activity.ActivityPolicy
import codekr.api.user.repository.UserRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 제출 코드 열람을 기록한다 (#136).
 *
 * **작성자가 켜 둔 경우에만 기록한다.** 그래서 대부분의 조회에는 쓰기가 아예 없다 —
 * 조회가 폭주해도 쓰기가 폭주하지 않는다는 요구를 설정 하나로 해결한다.
 */
@Component
class SubmissionViewRecorder(
    private val viewRepository: SubmissionViewRepository,
    private val userRepository: UserRepository,
) {

    /**
     * @param sourceVisible 소스 코드가 실제로 내려갔는가. **판정만 본 것은 세지 않는다** —
     *   목록에서도 보이는 정보이고, 코드를 읽은 것과 무게가 다르다.
     *
     * 조회 흐름을 막지 않도록 별도 트랜잭션에서 돈다. 기록이 실패해도 화면은 보여야 한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun record(authorId: Long, submissionId: Long, viewerId: Long?, sourceVisible: Boolean, isAdmin: Boolean) {
        if (!sourceVisible) return
        // 자기 것을 보는 것과 운영 행위는 알림이 될 이유가 없다.
        if (viewerId == null || viewerId == authorId || isAdmin) return

        val author = userRepository.findById(authorId).orElse(null) ?: return
        if (!author.viewNotificationEnabled) return

        viewRepository.record(submissionId, viewerId, Instant.now().atZone(ActivityPolicy.ZONE).toLocalDate())
    }

    /** 이 작성자가 열람 알림을 켜 두었는가. 조회자에게 그 사실을 알리는 데 쓴다. */
    @Transactional(readOnly = true)
    fun isEnabledFor(authorId: Long): Boolean =
        userRepository.findById(authorId).map { it.viewNotificationEnabled }.orElse(false)
}
