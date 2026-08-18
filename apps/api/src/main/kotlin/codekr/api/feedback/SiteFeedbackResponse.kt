package codekr.api.feedback

import java.time.Instant

/** 신고·제안 한 건 (#603). */
data class SiteFeedbackResponse(
    val id: Long,
    /** 넣은 회원. **비회원이 넣었으면 `null`** (#611). */
    val reporterId: Long?,
    /** 비회원이면 `(비회원)`. 어드민 목록에서 회원 것과 구별되어야 한다. */
    val reporterNickname: String,
    /** 비회원이 넣은 것인가. 화면이 색을 다르게 줄 수 있게 **따로 준다**. */
    val anonymous: Boolean,
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
            anonymous = feedback.reporterId == null,
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
