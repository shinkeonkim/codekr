package codekr.api.board.comment

import codekr.api.auth.security.AuthPrincipal
import codekr.api.board.repository.PostRepository
import codekr.api.common.error.ApiException
import codekr.api.notification.entity.NotificationCategory
import codekr.api.common.error.ErrorCode
import codekr.api.user.avatar.AvatarService
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import codekr.api.user.entity.WithdrawnUser
import codekr.api.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 댓글 (#138). */
@Service
@Transactional(readOnly = true)
class CommentService(
    private val notificationService: codekr.api.notification.service.NotificationService,
    private val commentRepository: CommentRepository,
    private val postRepository: PostRepository,
    private val userRepository: UserRepository,
) {

    /**
     * 한 글의 댓글 트리.
     *
     * **한 번의 조회로 만든다.** 부모마다 질의하면 댓글 수만큼 쿼리가 나간다.
     */
    fun findTree(postId: Long, principal: AuthPrincipal?): List<CommentResponse> {
        requirePost(postId)
        val comments = commentRepository.findByPostIdOrderByIdAsc(postId)
        if (comments.isEmpty()) return emptyList()

        val authors = userRepository.findAllById(comments.map { it.authorId }).associateBy { it.id }
        val byParent = comments.groupBy { it.parentId }

        // 자식이 하나도 남지 않은 삭제된 댓글은 아예 내리지 않는다 — 자리만 차지한다.
        fun build(parentId: Long?): List<CommentResponse> =
            byParent[parentId].orEmpty().mapNotNull { comment ->
                val children = build(comment.id)
                if (comment.isDeleted && children.isEmpty()) return@mapNotNull null
                responseOf(comment, authors, principal, children)
            }

        return build(null)
    }

    private val log = org.slf4j.LoggerFactory.getLogger(javaClass)

    @Transactional
    fun create(postId: Long, principal: AuthPrincipal, request: CommentUpsertRequest): List<CommentResponse> {
        requirePost(postId)
        request.parentId?.let { parentId ->
            val parent = commentRepository.findById(parentId).orElseThrow {
                ApiException(ErrorCode.COMMENT_NOT_FOUND)
            }
            // 다른 글의 댓글에 답을 달 수 없다. 그러면 트리가 두 글에 걸친다.
            if (parent.postId != postId) throw ApiException(ErrorCode.COMMENT_NOT_FOUND)
            // 삭제된 댓글에는 답을 달 수 없다 — 무엇에 답하는지가 사라진 자리다.
            if (parent.isDeleted) throw ApiException(ErrorCode.VALIDATION_ERROR, "삭제된 댓글에는 답할 수 없습니다.")
        }

        val saved = commentRepository.save(Comment(postId, request.parentId, principal.userId, request.body))
        notifyTarget(saved, principal.userId)
        return findTree(postId, principal)
    }

    @Transactional
    fun update(id: Long, principal: AuthPrincipal, request: CommentUpsertRequest): List<CommentResponse> {
        val comment = require(id)
        // 어드민도 남의 댓글을 고칠 수는 없다 (#137 과 같은 이유).
        if (comment.authorId != principal.userId) throw ApiException(ErrorCode.FORBIDDEN)
        comment.edit(request.body)
        return findTree(comment.postId, principal)
    }

    @Transactional
    fun delete(id: Long, principal: AuthPrincipal): List<CommentResponse> {
        val comment = require(id)
        if (comment.authorId != principal.userId && !canModerate(principal)) {
            throw ApiException(ErrorCode.FORBIDDEN)
        }
        // **자식까지 지우지 않는다.** 지우면 남의 글이 함께 사라진다.
        comment.delete()
        return findTree(comment.postId, principal)
    }

    fun countOf(postId: Long): Long = commentRepository.countByPostIdAndDeletedAtIsNull(postId)

    private fun responseOf(
        comment: Comment,
        authors: Map<Long, User>,
        principal: AuthPrincipal?,
        children: List<CommentResponse>,
    ): CommentResponse {
        val author = authors[comment.authorId]
        val deleted = comment.isDeleted
        val edited = !deleted && comment.updatedAt.isAfter(comment.createdAt.plusSeconds(EDIT_GRACE_SECONDS))

        return CommentResponse(
            id = comment.id,
            // 삭제된 댓글은 작성자도 내리지 않는다. 지운 사람이 누구인지 남길 이유가 없다.
            authorNickname = if (deleted) null else WithdrawnUser.nicknameOf(author),
            authorAvatarUrl = if (deleted) null else AvatarService.urlOf(WithdrawnUser.avatarKeyOf(author)),
            body = if (deleted) null else comment.body,
            deleted = deleted,
            createdAt = comment.createdAt,
            edited = edited,
            // 고친 적이 있을 때만 시각을 준다 — 없는 값을 화면이 걸러 내지 않게.
            editedAt = comment.updatedAt.takeIf { edited },
            editable = !deleted && principal != null && comment.authorId == principal.userId,
            deletable = !deleted && principal != null &&
                (comment.authorId == principal.userId || canModerate(principal)),
            children = children,
        )
    }

    private fun canModerate(principal: AuthPrincipal): Boolean =
        principal.has(UserRole.BOARD_MANAGER) ||
            principal.has(UserRole.ADMIN) ||
            principal.has(UserRole.SUPERUSER)

    private fun require(id: Long): Comment {
        val comment = commentRepository.findById(id).orElseThrow { ApiException(ErrorCode.COMMENT_NOT_FOUND) }
        if (comment.isDeleted) throw ApiException(ErrorCode.COMMENT_NOT_FOUND)
        return comment
    }

    private fun requirePost(postId: Long) {
        postRepository.findByIdAndDeletedAtIsNull(postId) ?: throw ApiException(ErrorCode.POST_NOT_FOUND)
    }

    /**
     * 답이 달렸다고 알린다 (#212).
     *
     * **두 경우의 문구와 대상이 다르다** — 내 글에 댓글이 달린 것과 내 댓글에 답이
     * 달린 것은 눌러 갈 곳은 같아도 읽는 사람에게는 다른 일이다.
     *
     * **알림이 실패해도 댓글은 저장된다.** 대화가 알림 때문에 끊기면 안 된다.
     */
    private fun notifyTarget(comment: Comment, writerId: Long) {
        runCatching {
            val post = postRepository.findByIdAndDeletedAtIsNull(comment.postId) ?: return
            val parent = comment.parentId?.let { commentRepository.findById(it).orElse(null) }

            val targetId = parent?.authorId ?: post.authorId
            // 자기 글에 자기가 단 댓글은 알리지 않는다.
            if (targetId == writerId) return
            // 탈퇴한 사람에게는 받을 곳이 없다 (#140).
            if (userRepository.findById(targetId).map { it.isWithdrawn }.orElse(true)) return

            val title = if (parent != null) "내 댓글에 답이 달렸습니다" else "내 글에 댓글이 달렸습니다"
            notificationService.notify(
                userId = targetId,
                category = NotificationCategory.COMMENT,
                title = title,
                // 글 제목을 함께 준다 — 목록에서 어느 대화인지 알아야 누를지 정한다.
                body = post.title,
                // **그 댓글 자리로 간다.** 글만 열면 긴 스레드에서 다시 찾아야 한다.
                link = "/posts/${comment.postId}#comment-${comment.id}",
            )
        }.onFailure { log.error("댓글 알림 실패 commentId={}", comment.id, it) }
    }

    private companion object {
        /** 저장 직후의 미세한 시각 차이로 모든 댓글에 (수정됨)이 붙지 않게. */
        const val EDIT_GRACE_SECONDS = 5L
    }
}
