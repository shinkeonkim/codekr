package codekr.api.board.dto

import codekr.api.board.entity.Board
import codekr.api.board.entity.Post
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

/** 게시글 작성·수정 (#137). */
data class PostUpsertRequest(
    val board: Board,

    @field:NotBlank(message = "제목이 필요합니다.")
    @field:Size(max = 200)
    val title: String,

    @field:NotBlank(message = "본문이 필요합니다.")
    @field:Size(max = MAX_BODY_LENGTH)
    val body: String,
) {
    companion object {
        /** 코드 블록이 들어가므로 짧게 잡지 않는다. */
        const val MAX_BODY_LENGTH = 50_000
    }
}

data class PostSummaryResponse(
    val id: Long,
    val board: Board,
    val boardLabel: String,
    val title: String,
    val authorNickname: String,
    val authorAvatarUrl: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    /** 수정된 글인지. 목록에서 "고쳐졌다" 는 사실이 보여야 한다. */
    val edited: Boolean,
    /** 댓글 수 (#138). 답이 달렸는지가 목록에서 보여야 질문 글이 쓸모가 있다. */
    val commentCount: Long = 0,
) {
    companion object {
        fun of(post: Post, nickname: String, avatarUrl: String?, commentCount: Long = 0) = PostSummaryResponse(
            id = post.id,
            board = post.board,
            boardLabel = post.board.label,
            title = post.title,
            authorNickname = nickname,
            authorAvatarUrl = avatarUrl,
            createdAt = post.createdAt,
            updatedAt = post.updatedAt,
            edited = post.updatedAt.isAfter(post.createdAt.plusSeconds(EDIT_GRACE_SECONDS)),
            commentCount = commentCount,
        )

        /**
         * 이 시간 안의 수정은 "고쳐졌다" 로 보지 않는다.
         *
         * 저장 직후의 미세한 시각 차이로 모든 글에 (수정됨)이 붙으면 그 표시가 뜻을 잃는다.
         */
        private const val EDIT_GRACE_SECONDS = 5L
    }
}

data class PostDetailResponse(
    val summary: PostSummaryResponse,
    /** 마크다운 원문. 렌더링은 화면이 한다 (#137 의 XSS 방어). */
    val body: String,
    val editable: Boolean,
    val deletable: Boolean,
)

data class BoardOption(val value: Board, val label: String, val description: String, val writable: Boolean)
