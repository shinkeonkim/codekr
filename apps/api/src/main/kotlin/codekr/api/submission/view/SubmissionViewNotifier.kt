package codekr.api.submission.view

import codekr.api.notification.entity.NotificationCategory
import codekr.api.notification.service.NotificationService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate

/**
 * 어제 몇 명이 내 코드를 봤는지 하루 한 번 알린다 (#136).
 *
 * **건별로 알리지 않는다.** 인기 문제의 공개 코드는 하루에 수백 번 읽힐 수 있고,
 * 한 건마다 알림이면 알림함이 이것으로만 찬다.
 */
@Component
class SubmissionViewNotifier(
    private val viewRepository: SubmissionViewRepository,
    private val notificationService: NotificationService,
    /**
     * 오늘이 며칠인지 정하는 시계 (#241). 시간대를 이것이 들고 있다.
     *
     * 주입받는 이유: 날짜 경계에서만 나는 문제는 시계를 고정해야 재현된다.
     */
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 새벽에 돈다. 어제치를 세므로 그날이 끝난 뒤여야 한다. */
    @Scheduled(cron = "0 10 5 * * *", zone = "Asia/Seoul")
    @Transactional
    fun notifyYesterday() {
        val day = LocalDate.now(clock).minusDays(1)
        val counts = viewRepository.dailyViewerCounts(day)
        if (counts.isEmpty()) return

        counts.forEach { count ->
            notificationService.notify(
                userId = count.authorId,
                category = NotificationCategory.SUBMISSION_VIEW,
                title = "어제 ${count.viewerCount}명이 내 코드를 봤습니다",
                // **누가 봤는지는 담지 않는다.** 수만 알려도 "읽히고 있다" 는 목적은 이룬다.
                body = "제출 ${count.submissionCount}건이 열람되었습니다.",
                link = "/submissions",
            )
        }
        log.info("코드 열람 알림 {}건 발송 ({})", counts.size, day)
    }

    /** 보관 기간이 지난 열람 기록을 지운다 (ADR-0007). */
    @Scheduled(cron = "0 20 5 * * *", zone = "Asia/Seoul")
    @Transactional
    fun purgeOld() {
        val removed = viewRepository.deleteOlderThan(LocalDate.now(clock).minusDays(RETENTION_DAYS))
        if (removed > 0) log.info("오래된 열람 기록 {}건 삭제", removed)
    }

    private companion object {
        /**
         * 열람 기록 보관 기간.
         *
         * 알림을 만들고 나면 원자료가 할 일은 끝난다. 오래 두면 **누가 무엇을 언제 봤는지**가
         * 계속 쌓이는데, 그것은 이 기능이 필요로 하지 않는 정보다.
         */
        const val RETENTION_DAYS = 30L
    }
}
