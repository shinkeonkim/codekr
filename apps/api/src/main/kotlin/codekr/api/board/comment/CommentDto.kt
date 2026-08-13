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
    /** 어느 댓글에 달린 답인지 (#213). 화면이 이어받은 것을 제자리에 끼워 넣을 때 쓴다. */
    val parentId: Long?,
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
    /**
     * 아직 안 내려온 답글 수 (#213).
     *
     * **없으면 화면이 "더 있는지" 를 알 수 없다.** 접힌 자리에 개수가 안 보이면
     * 펼칠 이유도 모른다.
     */
    val remainingChildren: Int,
    /**
     * 본문이 부른 사람들 (#214). 표기를 이름으로 바꾸는 데 쓴다.
     *
     * **본문과 함께 내린다** — 화면이 id 로 다시 조회하면 댓글 수만큼 요청이 나가고,
     * 하나라도 실패하면 멘션이 표기 그대로 보인다.
     */
    val mentions: List<codekr.api.board.mention.MentionResponse>,
)

/**
 * 잘라서 내리는 댓글 트리 (#213).
 *
 * 전체 수를 **서버가 센다** — 화면이 받은 트리를 세던 방식은 잘라 내리기 시작하면
 * 곧바로 틀린 수가 된다.
 */
data class CommentTreeResponse(
    val comments: List<CommentResponse>,
    /** 삭제 규칙까지 반영한 이 글의 댓글 수. */
    val totalCount: Int,
    /** 최상위에 아직 안 내려온 수. */
    val remainingTop: Int,
)
