package codekr.api.contest.audit

import codekr.api.contest.repository.ContestRepository
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

/** 대회 제출의 감사 이력 (#148). */
@Service
class ContestAuditService(
    private val auditRepository: ContestAuditRepository,
    private val contestRepository: ContestRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 대회 제출을 기록한다.
     *
     * **기록 실패가 제출을 막지 않는다.** 감사 이력은 사후 판단용이고, 그것 때문에
     * 참가자가 제출하지 못하면 본말이 뒤집힌다.
     */
    @Transactional
    fun record(submissionId: Long, contestId: Long, userId: Long, request: HttpServletRequest) {
        runCatching {
            auditRepository.record(submissionId, contestId, userId, clientIp(request), request.getHeader("User-Agent"))
        }.onFailure { log.warn("감사 이력 기록 실패: submissionId={} {}", submissionId, it.message) }
    }

    @Transactional(readOnly = true)
    fun sharedAddresses(contestId: Long): List<SharedAddress> = auditRepository.sharedAddresses(contestId)

    /**
     * 보관 기간이 지난 기록을 지운다 (ADR-0007).
     *
     * 대회 종료 후 [RETENTION_DAYS] 일. 이의 제기와 표절 검토가 그 안에 끝난다고 본다.
     * 더 오래 두면 **이 기능이 필요로 하지 않는 정보**가 계속 쌓인다.
     */
    @Scheduled(cron = "0 30 5 * * *", zone = "Asia/Seoul")
    @Transactional
    fun purgeOld() {
        val removed = auditRepository.deleteOlderThan(Instant.now().minus(Duration.ofDays(RETENTION_DAYS)))
        if (removed > 0) log.info("오래된 대회 감사 이력 {}건 삭제", removed)
    }

    /**
     * 클라이언트 주소.
     *
     * 프록시 뒤에 있으면 `X-Forwarded-For` 의 **맨 앞**이 원 주소다. 다만 이 헤더는
     * 클라이언트가 보낼 수도 있으므로, 신뢰할 수 있는 프록시 뒤에서만 뜻이 있다 —
     * 그 사실을 문서에 적어 둔다.
     */
    private fun clientIp(request: HttpServletRequest): String? =
        request.getHeader("X-Forwarded-For")
            ?.split(",")
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: request.remoteAddr

    private companion object {
        const val RETENTION_DAYS = 90L
    }
}
