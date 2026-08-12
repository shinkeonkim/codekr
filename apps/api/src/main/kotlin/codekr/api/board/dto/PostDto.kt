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

    /** 문제에 붙는 질문이면 그 문제 (#139). 커뮤니티 글이면 비운다. */
    val problemId: Long? = null,
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
    /** 문제에 붙은 질문이면 그 문제. 목록에서 어느 문제의 질문인지 보여야 한다 (#139). */
    val problemId: Long? = null,
    val problemSlug: String? = null,
    val problemTitle: String? = null,
) {
    companion object {
        fun of(
            post: Post,
            nickname: String,
            avatarUrl: String?,
            commentCount: Long = 0,
            problemId: Long? = null,
            problemSlug: String? = null,
            problemTitle: String? = null,
        ) = PostSummaryResponse(
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
            problemId = problemId,
            problemSlug = problemSlug,
            problemTitle = problemTitle,
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
    /**
     * 코드 블록을 기본으로 가릴지 (#139).
     *
     * **문제 질문에는 정답 코드가 그대로 올라온다.** 아직 못 푼 사람에게 답이 보이면
     * 그 문제의 값이 떨어진다. 그렇다고 "푼 사람에게만" 으로 막으면
     * **질문하려면 먼저 풀어야 한다** 는 모순이 생긴다 — 그래서 가리고, 펼칠 수 있게 한다.
     */
    val hideCode: Boolean = false,
    /** 마크다운 원문. 렌더링은 화면이 한다 (#137 의 XSS 방어). */
    val body: String,
    val editable: Boolean,
    val deletable: Boolean,
)

data class BoardOption(val value: Board, val label: String, val description: String, val writable: Boolean)
