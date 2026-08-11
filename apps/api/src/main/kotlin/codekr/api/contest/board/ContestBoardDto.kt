package codekr.api.contest.board

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class NoticeUpsertRequest(
    @field:NotBlank(message = "제목이 필요합니다.")
    @field:Size(max = 200)
    val title: String,

    @field:NotBlank(message = "내용이 필요합니다.")
    @field:Size(max = 20_000)
    val body: String,
)

data class NoticeResponse(
    val id: Long,
    val title: String,
    val body: String,
    val createdAt: Instant,
    val edited: Boolean,
) {
    companion object {
        fun from(notice: ContestNotice) = NoticeResponse(
            id = notice.id,
            title = notice.title,
            body = notice.body,
            createdAt = notice.createdAt,
            edited = notice.updatedAt.isAfter(notice.createdAt.plusSeconds(5)),
        )
    }
}

data class QuestionRequest(
    /** 어느 문제에 대한 질문인지. 비우면 대회 전체에 대한 질문이다. */
    val problemId: Long? = null,

    @field:NotBlank(message = "내용이 필요합니다.")
    @field:Size(max = 5_000)
    val body: String,
)

data class AnswerRequest(
    @field:NotBlank(message = "답변이 필요합니다.")
    @field:Size(max = 20_000)
    val answer: String,

    /**
     * 전원에게 공개할지.
     *
     * **기본은 비공개다.** 공개는 되돌릴 수 없다 — 이미 본 사람에게서 지울 수 없다.
     */
    val public: Boolean = false,
)

data class QuestionResponse(
    val id: Long,
    val problemLabel: String?,
    val body: String,
    val answer: String?,
    val answerPublic: Boolean,
    val answeredAt: Instant?,
    val createdAt: Instant,
    /** 내가 낸 질문인가. 목록에서 내 것을 찾을 수 있어야 한다. */
    val mine: Boolean,
)
