package codekr.api.feedback

import java.time.Instant

/** 신고·제안 한 건 (#603). */
data class SiteFeedbackResponse(
    val id: Long,
    val reporterId: Long,
    val reporterNickname: String,
    val kind: FeedbackKind,
    val kindLabel: String,
    val body: String,
    val pageUrl: String?,
    val status: FeedbackStatus,
    val statusLabel: String,
    val resolution: String?,
    val createdAt: Instant,
) {
    companion object {
        fun from(feedback: SiteFeedback, nickname: String) = SiteFeedbackResponse(
            id = feedback.id,
            reporterId = feedback.reporterId,
            reporterNickname = nickname,
            kind = feedback.kind,
            kindLabel = feedback.kind.label,
            body = feedback.body,
            pageUrl = feedback.pageUrl,
            status = feedback.status,
            statusLabel = feedback.status.label,
            resolution = feedback.resolution,
            createdAt = feedback.createdAt,
        )
    }
}
