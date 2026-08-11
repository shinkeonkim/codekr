package codekr.api.contest.service

import codekr.api.contest.entity.ContestStatus
import codekr.api.contest.repository.ContestRegistrationRepository
import codekr.api.contest.repository.ContestRepository
import codekr.api.scaling.service.ExecutorScaleService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

/**
 * 대회 시작 전 실행기를 미리 늘린다 (#62, #40 연동).
 *
 * **시작하고 나서 늘리면 늦다.** 파드가 뜨고 런타임 이미지를 확인하는 데 시간이 걸리는데,
 * 그 사이가 대회에서 가장 붐비는 순간이다 — 모두가 동시에 첫 문제를 낸다.
 *
 * 줄이는 일은 하지 않는다. 언제 줄여도 안전한지 판단하려면 큐가 비었는지, 다른 대회가
 * 곧 시작하는지, 사람이 손으로 올린 값인지를 알아야 한다. **잘못 줄이면 채점이 멈추므로
 * 사람이 판단하게 둔다** — 어드민 화면에서 언제든 줄일 수 있다 (#40).
 */
@Component
class ContestPrescaler(
    private val contestRepository: ContestRepository,
    private val registrationRepository: ContestRegistrationRepository,
    private val scaleService: ExecutorScaleService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${codekr.contest.prescale-check-interval-ms:60000}")
    @Transactional(readOnly = true)
    fun prescale() {
        val now = Instant.now()
        val upcoming = contestRepository
            .findByStatusAndStartsAtBetween(ContestStatus.PUBLISHED, now, now.plus(LEAD_TIME))
        if (upcoming.isEmpty()) return

        val status = scaleService.status()
        if (!status.available) return

        val needed = upcoming.sumOf { targetFor(registrationRepository.countByIdContestId(it.id)) }
        // **줄이지 않는다.** 이미 더 많이 떠 있다면 그럴 이유가 있는 것이다.
        if (needed <= status.desiredReplicas) return

        val target = needed.coerceAtMost(status.maxReplicas)
        log.info(
            "대회 시작 전 실행기를 늘립니다: {} → {} (대회 {}개, 참가 {}명)",
            status.desiredReplicas,
            target,
            upcoming.size,
            upcoming.sumOf { registrationRepository.countByIdContestId(it.id) },
        )
        runCatching { scaleService.scale(target) }
            .onFailure { log.warn("사전 스케일 아웃 실패: {}", it.message) }
    }

    /**
     * 참가자 수로 필요한 실행기 수를 어림한다.
     *
     * 참가자 [PARTICIPANTS_PER_EXECUTOR] 명당 하나. 정밀한 값이 아니라 **시작 직후의
     * 첫 파도를 견디는 수**다 — 그 뒤로는 큐 적체를 보고 사람이 조정한다.
     */
    private fun targetFor(participants: Int): Int =
        ((participants + PARTICIPANTS_PER_EXECUTOR - 1) / PARTICIPANTS_PER_EXECUTOR).coerceAtLeast(1)

    private companion object {
        /** 시작 몇 분 전부터 늘릴지. 파드가 뜨고 준비되기까지의 시간을 감안한다. */
        val LEAD_TIME: Duration = Duration.ofMinutes(15)

        const val PARTICIPANTS_PER_EXECUTOR = 20
    }
}
