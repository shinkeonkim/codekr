package codekr.api.board.comment

import codekr.api.auth.security.AuthPrincipal
import codekr.api.user.entity.User

/**
 * 받아 온 댓글을 **잘라서** 트리로 만든다 (#213).
 *
 * 전에는 글의 댓글을 전부 그대로 내렸다. 깊이 제한이 없는 구조(#138)에서 그것은
 * **두 사람이 주고받은 스레드 하나가 다른 사람들의 댓글을 화면 밖으로 밀어내는** 결과가
 * 된다. 응답과 렌더도 함께 무거워진다.
 *
 * **깊이와 개수를 둘 다 본다.** 깊이만 보면 넓고 얕은 스레드(한 댓글에 답글 50개)가
 * 안 잡히고, 개수만 보면 깊게 파고든 2인 대화가 안 잡힌다.
 *
 * 자르는 단위는 **부모 기준**이다 — 부모를 자르면 그 아래 자식도 함께 빠지므로,
 * 자식만 남고 부모가 없는 응답이 나오지 않는다.
 */
class CommentTreeAssembler(
    private val comments: List<Comment>,
    private val authors: Map<Long, User>,
    private val principal: AuthPrincipal?,
    /** 이 안에 든 댓글은 자르지 않고 끝까지 편다 — 알림·링크로 들어온 자리다 (#212). */
    private val expanded: Set<Long> = emptySet(),
) {
    private val byParent: Map<Long?, List<Comment>> = comments.groupBy { it.parentId }

    /** 화면에 보일 수 있는 댓글만 센다 — 자식이 없는 삭제 댓글은 애초에 내리지 않는다. */
    fun visibleCount(): Int = countVisible(null)

    /**
     * 한 부모 아래를 [limit] 개까지 만든다.
     *
     * [after] 는 **마지막으로 받은 댓글 id** 다. 오프셋이 아니라 커서인 이유는,
     * 읽는 사이에 새 댓글이 달리면 오프셋이 **이미 본 댓글을 다시 주거나 건너뛰기**
     * 때문이다. 트리에서는 같은 댓글이 두 번 그려지는 쪽이 더 나쁘다.
     */
    fun build(parentId: Long?, depth: Int, limit: Int, after: Long? = null): List<CommentResponse> {
        val siblings = visibleSiblings(parentId).filter { after == null || it.id > after }
        return siblings.take(limit).map { comment ->
            val childLimit = if (depth + 1 >= MAX_DEPTH && comment.id !in expanded) 0 else CHILD_PAGE
            val children = build(comment.id, depth + 1, childLimit)
            val remaining = visibleSiblings(comment.id).size - children.size
            responseOf(comment, children, remaining)
        }
    }

    /** 잘린 자리에 몇 개가 남았는지. 없으면 화면이 "더 있는지" 를 알 수 없다. */
    fun remaining(parentId: Long?, shown: Int, after: Long? = null): Int =
        visibleSiblings(parentId).count { after == null || it.id > after } - shown

    private fun visibleSiblings(parentId: Long?): List<Comment> =
        byParent[parentId].orEmpty().filter { it.isVisible() }

    /** 자식이 하나도 남지 않은 삭제된 댓글은 내리지 않는다 — 자리만 차지한다. */
    private fun Comment.isVisible(): Boolean = !isDeleted || countVisible(id) > 0

    private fun countVisible(parentId: Long?): Int =
        byParent[parentId].orEmpty().sumOf { child ->
            val below = countVisible(child.id)
            if (child.isDeleted && below == 0) 0 else 1 + below
        }

    private fun responseOf(comment: Comment, children: List<CommentResponse>, remaining: Int): CommentResponse {
        val author = authors[comment.authorId]
        val deleted = comment.isDeleted
        val edited = !deleted && comment.updatedAt.isAfter(comment.createdAt.plusSeconds(EDIT_GRACE_SECONDS))

        return CommentResponse(
            id = comment.id,
            parentId = comment.parentId,
            // 삭제된 댓글은 작성자도 내리지 않는다. 지운 사람이 누구인지 남길 이유가 없다.
            authorNickname = if (deleted) null else codekr.api.user.entity.WithdrawnUser.nicknameOf(author),
            authorAvatarUrl = if (deleted) {
                null
            } else {
                codekr.api.user.avatar.AvatarService.urlOf(codekr.api.user.entity.WithdrawnUser.avatarKeyOf(author))
            },
            body = if (deleted) null else comment.body,
            deleted = deleted,
            createdAt = comment.createdAt,
            edited = edited,
            editedAt = comment.updatedAt.takeIf { edited },
            editable = !deleted && principal != null && comment.authorId == principal.userId,
            deletable = !deleted && principal != null &&
                (comment.authorId == principal.userId || principal.canModerate()),
            children = children,
            remainingChildren = remaining,
        )
    }

    private fun AuthPrincipal.canModerate(): Boolean =
        roles.any {
            it == codekr.api.user.entity.UserRole.BOARD_MANAGER ||
                it == codekr.api.user.entity.UserRole.ADMIN ||
                it == codekr.api.user.entity.UserRole.SUPERUSER
        }

    companion object {
        /** 최상위 댓글을 한 번에 내리는 수. */
        const val TOP_PAGE = 20

        /** 한 부모의 답글을 한 번에 내리는 수. */
        const val CHILD_PAGE = 3

        /** 이 깊이부터는 답글을 접는다. 0 이 최상위다. */
        const val MAX_DEPTH = 3

        /** 저장 직후의 미세한 시각 차이로 모든 댓글에 (수정됨)이 붙지 않게. */
        const val EDIT_GRACE_SECONDS = 5L
    }
}
