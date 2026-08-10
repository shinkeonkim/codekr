package codekr.api.board.comment

import codekr.api.auth.security.AuthPrincipal
import codekr.api.board.repository.PostRepository
import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.user.avatar.AvatarService
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import codekr.api.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 댓글 (#138). */
@Service
@Transactional(readOnly = true)
class CommentService(
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

        commentRepository.save(Comment(postId, request.parentId, principal.userId, request.body))
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

        return CommentResponse(
            id = comment.id,
            // 삭제된 댓글은 작성자도 내리지 않는다. 지운 사람이 누구인지 남길 이유가 없다.
            authorNickname = if (deleted) null else author?.nickname ?: "(탈퇴한 사용자)",
            authorAvatarUrl = if (deleted) null else AvatarService.urlOf(author?.avatarKey),
            body = if (deleted) null else comment.body,
            deleted = deleted,
            createdAt = comment.createdAt,
            edited = !deleted && comment.updatedAt.isAfter(comment.createdAt.plusSeconds(EDIT_GRACE_SECONDS)),
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

    private companion object {
        /** 저장 직후의 미세한 시각 차이로 모든 댓글에 (수정됨)이 붙지 않게. */
        const val EDIT_GRACE_SECONDS = 5L
    }
}
