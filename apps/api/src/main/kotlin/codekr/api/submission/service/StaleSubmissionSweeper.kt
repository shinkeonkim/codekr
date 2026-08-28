package codekr.api.submission.service

import codekr.api.config.properties.SubmissionProperties
import codekr.api.observability.MetricNames
import codekr.api.submission.entity.SubmissionStatus
import codekr.api.submission.repository.SubmissionRepository
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
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
    registry: MeterRegistry,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 조용히 늘면 큰일이다 (#684).
     *
     * 여기까지 온 제출은 사용자에게 "채점 실패" 로만 보인다. 지금까지 이것은 로그
     * 한 줄이었고, **몇 건인지 세어 본 적이 없다.** 로그는 몇 건인지 남기지만 시계열로
     * 남지 않아 "지난주보다 늘었나" 를 물을 수 없다.
     */
    private val closed: Counter = Counter.builder(MetricNames.STALE_CLOSED)
        .description("채점이 끝나지 않아 SYSTEM_ERROR 로 닫은 제출 수 (ADR-0004)")
        .register(registry)

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
        closed.increment(stale.size.toDouble())
        log.warn("채점이 끝나지 않은 제출 {}건을 SYSTEM_ERROR 로 종결했습니다.", stale.size)
    }
}
