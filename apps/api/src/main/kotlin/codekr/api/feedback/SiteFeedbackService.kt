package codekr.api.feedback

import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.notification.entity.NotificationCategory
import codekr.api.notification.service.NotificationService
import codekr.api.user.repository.UserRepository
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 사이트 신고·제안 (#603).
 *
 * ## 받는 것만으로는 절반이다
 *
 * 말없이 닫으면 넣은 사람에게는 **읽지 않은 것과 구분되지 않는다.** 그래서 처리하면
 * 반드시 알린다(#106). 거절에는 이유가 있어야 한다 — 이유 없는 거절은 다음 제안을
 * 막는다. 문제 오류 신고(#478)가 같은 이유로 같은 규칙을 갖고 있다.
 *
 * ## 한 사람이 열 개를 열어 두지 않는다
 *
 * 열 명이 같은 것을 말하는 것은 정보지만(그만큼 급하다), 한 사람이 열 번 말하는 것은
 * 목록만 흐린다. 다만 **문제 신고보다는 느슨하게 둔다** — 저기는 "이 문제" 라는 범위가
 * 있어 중복인지 바로 알 수 있지만, 여기는 서로 다른 화면의 이야기가 섞인다.
 */
@Service
class SiteFeedbackService(
    private val feedbackRepository: SiteFeedbackRepository,
    private val userRepository: UserRepository,
    private val notificationService: NotificationService,
) {

    @Transactional
    fun submit(reporterId: Long, kind: FeedbackKind, body: String, pageUrl: String?): SiteFeedbackResponse {
        if (body.isBlank()) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "무엇을 말하려는지 적어 주세요.")
        }
        if (feedbackRepository.countByReporterIdAndStatus(reporterId, FeedbackStatus.OPEN) >= OPEN_LIMIT) {
            throw ApiException(
                ErrorCode.VALIDATION_ERROR,
                "아직 처리되지 않은 것이 $OPEN_LIMIT 건 있습니다. 처리되면 다시 넣어 주세요.",
            )
        }
        val saved = feedbackRepository.save(
            SiteFeedback(reporterId, kind, body.trim(), pageUrl?.trim()?.take(PAGE_URL_LIMIT)?.ifBlank { null }),
        )
        return response(saved)
    }

    @Transactional(readOnly = true)
    fun list(status: FeedbackStatus?, pageable: Pageable) =
        (status?.let { feedbackRepository.findByStatusOrderByIdDesc(it, pageable) }
            ?: feedbackRepository.findAllByOrderByIdDesc(pageable))
            .map(::response)

    /** 내가 넣은 것. **어디로 갔는지 볼 수 있어야 다시 넣는다.** */
    @Transactional(readOnly = true)
    fun listMine(reporterId: Long, pageable: Pageable) =
        feedbackRepository.findByReporterIdOrderByIdDesc(reporterId, pageable).map(::response)

    @Transactional
    fun resolve(id: Long, adminId: Long, status: FeedbackStatus, resolution: String?): SiteFeedbackResponse {
        val feedback = feedbackRepository.findById(id).orElseThrow {
            ApiException(ErrorCode.VALIDATION_ERROR, "없는 신고입니다.")
        }
        if (feedback.status != FeedbackStatus.OPEN) {
            // 두 번 처리되면 알림이 두 번 가고, 어느 것이 결론인지 알 수 없다.
            throw ApiException(ErrorCode.VALIDATION_ERROR, "이미 처리된 신고입니다.")
        }
        if (status == FeedbackStatus.REJECTED && resolution.isNullOrBlank()) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "반영하지 않으려면 이유를 적어야 합니다.")
        }
        feedback.resolve(status, resolution?.trim(), adminId)

        notificationService.notify(
            userId = feedback.reporterId,
            category = NotificationCategory.SYSTEM,
            title = if (status == FeedbackStatus.ACCEPTED) "보내 주신 의견을 반영했습니다" else "보내 주신 의견을 처리했습니다",
            body = feedback.resolution,
            link = "/feedback",
        )
        return response(feedback)
    }

    private fun response(feedback: SiteFeedback) = SiteFeedbackResponse.from(
        feedback,
        userRepository.findById(feedback.reporterId).map { it.nickname }.orElse("(탈퇴한 회원)"),
    )

    companion object {
        /** 한 사람이 동시에 열어 둘 수 있는 수. 목록이 한 사람 것으로 덮이지 않게. */
        private const val OPEN_LIMIT = 5
        private const val PAGE_URL_LIMIT = 500
    }
}
