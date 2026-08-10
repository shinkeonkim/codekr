package codekr.api.submission.service

import codekr.api.config.properties.SubmissionProperties
import codekr.api.submission.entity.SubmissionStatus
import codekr.api.submission.repository.SubmissionRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 채점이 끝나지 않은 채 방치된 제출을 종결한다.
 *
 * 이벤트 기반 영속화는 Pub/Sub 전달 보장이 없어 이벤트가 유실될 수 있다 (ADR-0004).
 * 사용자가 영원히 "채점 중" 화면을 보는 상황을 막는 마지막 안전망이다.
 */
@Component
class StaleSubmissionSweeper(
    private val submissionRepository: SubmissionRepository,
    private val properties: SubmissionProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "PT30S")
    @Transactional
    fun sweep() {
        val threshold = Instant.now().minusSeconds(properties.staleTimeoutSeconds)
        val stale = submissionRepository.findByStatusInAndCreatedAtBefore(
            listOf(SubmissionStatus.PENDING, SubmissionStatus.JUDGING),
            threshold,
        )
        if (stale.isEmpty()) return

        stale.forEach { it.fail() }
        log.warn("채점이 끝나지 않은 제출 {}건을 SYSTEM_ERROR 로 종결했습니다.", stale.size)
    }
}
