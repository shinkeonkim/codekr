package codekr.api.board.comment

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class CommentUpsertRequest(
    /** 없으면 최상위 댓글이다. */
    val parentId: Long? = null,

    @field:NotBlank(message = "내용이 필요합니다.")
    @field:Size(max = MAX_BODY_LENGTH)
    val body: String,
) {
    companion object {
        const val MAX_BODY_LENGTH = 10_000
    }
}

/**
 * 댓글 하나 (#138).
 *
 * 트리는 [children] 으로 이어진다. 서버가 트리를 만들어 내리는 이유는 화면마다
 * 잇는 규칙을 다시 쓰지 않게 하기 위함이다 — 그 규칙이 갈라지면 화면마다 순서가 달라진다.
 */
data class CommentResponse(
    val id: Long,
    val authorNickname: String?,
    val authorAvatarUrl: String?,
    /** 삭제된 댓글은 본문을 내리지 않는다. 자리만 남는다. */
    val body: String?,
    val deleted: Boolean,
    val createdAt: Instant,
    val edited: Boolean,
    /**
     * 고친 시각 (#211). 고친 적이 없으면 null.
     *
     * **`edited` 만으로는 부족하다.** 누군가 답을 단 뒤에 원글을 고치면 대화가 어긋나
     * 보이는데, 언제 고쳤는지가 없으면 읽는 사람은 답글 쓴 사람이 잘못 읽었다고
     * 생각한다.
     */
    val editedAt: Instant?,
    val editable: Boolean,
    val deletable: Boolean,
    val children: List<CommentResponse>,
)
