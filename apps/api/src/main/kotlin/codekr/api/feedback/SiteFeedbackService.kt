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
    private val rateLimiter: AnonymousFeedbackRateLimiter,
) {

    /**
     * 로그인하지 못하는 사람이 넣는다 (#611).
     *
     * **가장 급한 신고가 로그인 바깥에 있다** — "가입이 안 됩니다", "인증 메일이 안
     * 옵니다" 는 로그인해서 넣을 수 없다.
     *
     * **답을 돌려주지 않는다.** 답하려면 연락처를 받아야 하는데, 그것은 **가입 없이
     * 개인정보를 모으는 일**이라 약관(#235)이 다루지 않는 자리가 된다. 접수됐다는
     * 사실만 알린다.
     */
    @Transactional
    fun submitAnonymously(
        kind: FeedbackKind,
        body: String,
        pageUrl: String?,
        clientKey: String,
    ): SiteFeedbackResponse {
        if (body.isBlank()) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "무엇을 말하려는지 적어 주세요.")
        }
        if (!rateLimiter.allow(clientKey)) {
            throw ApiException(
                ErrorCode.TOO_MANY_REQUESTS,
                "잠시 뒤에 다시 보내 주세요. 같은 곳에서 너무 자주 들어왔습니다.",
            )
        }
        val saved = feedbackRepository.save(
            SiteFeedback(
                reporterId = null,
                kind = kind,
                body = body.trim(),
                pageUrl = pageUrl?.trim()?.take(PAGE_URL_LIMIT)?.ifBlank { null },
                reporterHint = clientKey.take(HINT_LIMIT),
            ),
        )
        return response(saved)
    }

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
            SiteFeedback(
                reporterId = reporterId,
                kind = kind,
                body = body.trim(),
                pageUrl = pageUrl?.trim()?.take(PAGE_URL_LIMIT)?.ifBlank { null },
            ),
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

        // **비회원에게는 알릴 곳이 없다** (#611). 연락처를 받지 않기로 했기 때문이다.
        feedback.reporterId?.let { reporterId ->
            notificationService.notify(
                userId = reporterId,
                category = NotificationCategory.SYSTEM,
                title = if (status == FeedbackStatus.ACCEPTED) "보내 주신 의견을 반영했습니다" else "보내 주신 의견을 처리했습니다",
                body = feedback.resolution,
                link = "/feedback",
            )
        }
        return response(feedback)
    }

    /**
     * 넣은 사람의 이름. **비회원이면 그렇다고 적는다** (#611).
     *
     * 어드민 목록에서 회원 것과 구별되어야 한다 — 같은 목록에 섞여 있으면 "이 사람에게
     * 답을 줄 수 있나" 를 매번 다시 판단하게 된다.
     */
    private fun response(feedback: SiteFeedback) = SiteFeedbackResponse.from(
        feedback,
        feedback.reporterId
            ?.let { id -> userRepository.findById(id).map { it.nickname }.orElse("(탈퇴한 회원)") }
            ?: "(비회원)",
    )

    companion object {
        /** 한 사람이 동시에 열어 둘 수 있는 수. 목록이 한 사람 것으로 덮이지 않게. */
        private const val OPEN_LIMIT = 5
        private const val PAGE_URL_LIMIT = 500

        /** 출처 힌트 길이. 주소를 그대로 두지 않는다 — 되짚을 만큼만 남긴다 (#611). */
        private const val HINT_LIMIT = 60
    }
}
