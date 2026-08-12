package codekr.api.submission.view

import codekr.api.activity.ActivityPolicy
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 제출 코드 열람을 기록한다 (#136).
 *
 * **작성자 설정을 보지 않는다** (#199). 전에는 켜 둔 사람의 것만 기록해서 대부분의 조회에
 * 쓰기가 없었는데, 그 설정 자체가 걷혔다.
 *
 * 그래도 쓰기가 폭주하지 않는 이유는 **하루 한 번으로 묶기 때문**이다 — 같은 사람이 같은
 * 제출을 몇 번 열어도 그날의 행은 하나다 (`viewRepository.record` 의 upsert).
 * 남의 공개 코드를 읽는 일 자체가 흔하지 않다는 점도 있다.
 */
@Component
class SubmissionViewRecorder(private val viewRepository: SubmissionViewRepository) {

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

        viewRepository.record(submissionId, viewerId, Instant.now().atZone(ActivityPolicy.ZONE).toLocalDate())
    }
}
