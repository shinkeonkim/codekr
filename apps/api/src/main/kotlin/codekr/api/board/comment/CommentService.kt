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
     * 한 글의 댓글 트리 (#213).
     *
     * **한 번의 조회로 만든다** (#138). 잘라서 내릴 뿐, 부모마다 질의하는 구조로
     * 돌아가지 않는다 — 자르는 판단은 [CommentTreeAssembler] 가 메모리에서 한다.
     *
     * @param after 최상위를 이어받을 때 **마지막으로 받은 댓글 id**
     * @param around 이 댓글이 보이도록 그 조상들을 끝까지 편다 (알림·링크, #212)
     */
    fun findTree(
        postId: Long,
        principal: AuthPrincipal?,
        after: Long? = null,
        around: Long? = null,
    ): CommentTreeResponse {
        requirePost(postId)
        val comments = commentRepository.findByPostIdOrderByIdAsc(postId)
        if (comments.isEmpty()) return CommentTreeResponse(emptyList(), 0, 0)

        val authors = userRepository.findAllById(comments.map { it.authorId }).associateBy { it.id }
        val assembler = CommentTreeAssembler(comments, authors, principal, ancestorsOf(comments, around))

        val top = assembler.build(null, depth = 0, limit = CommentTreeAssembler.TOP_PAGE, after = after)
        return CommentTreeResponse(
            comments = top,
            totalCount = assembler.visibleCount(),
            remainingTop = assembler.remaining(null, top.size, after),
        )
    }

    /**
     * 한 부모의 답글을 이어받는다 (#213).
     *
     * 커서는 **그 부모 아래에서 마지막으로 받은 id** 다. 부모마다 따로 두는 것이
     * 트리에서 가장 단순하다 — "다음 N개" 가 트리에서는 자명하지 않다 (#138).
     */
    fun findChildren(commentId: Long, principal: AuthPrincipal?, after: Long?): CommentTreeResponse {
        val parent = require(commentId)
        val comments = commentRepository.findByPostIdOrderByIdAsc(parent.postId)
        val authors = userRepository.findAllById(comments.map { it.authorId }).associateBy { it.id }
        val assembler = CommentTreeAssembler(comments, authors, principal)

        val children = assembler.build(commentId, depth = 0, limit = CommentTreeAssembler.CHILD_PAGE, after = after)
        return CommentTreeResponse(
            comments = children,
            totalCount = assembler.visibleCount(),
            remainingTop = assembler.remaining(commentId, children.size, after),
        )
    }

    /** [around] 로 지목된 댓글이 보이도록, 그 조상들을 접지 않는 목록으로 만든다. */
    private fun ancestorsOf(comments: List<Comment>, around: Long?): Set<Long> {
        if (around == null) return emptySet()
        val byId = comments.associateBy { it.id }
        val chain = mutableSetOf<Long>()
        var cursor = byId[around]
        while (cursor != null && chain.add(cursor.id)) {
            cursor = cursor.parentId?.let { byId[it] }
        }
        return chain
    }

    private val log = org.slf4j.LoggerFactory.getLogger(javaClass)

    @Transactional
    fun create(postId: Long, principal: AuthPrincipal, request: CommentUpsertRequest): CommentTreeResponse {
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
        // 방금 쓴 댓글이 접힌 자리에 들어가면 쓴 사람이 자기 글을 못 본다.
        return findTree(postId, principal, around = saved.id)
    }

    @Transactional
    fun update(id: Long, principal: AuthPrincipal, request: CommentUpsertRequest): CommentTreeResponse {
        val comment = require(id)
        // 어드민도 남의 댓글을 고칠 수는 없다 (#137 과 같은 이유).
        if (comment.authorId != principal.userId) throw ApiException(ErrorCode.FORBIDDEN)
        comment.edit(request.body)
        return findTree(comment.postId, principal, around = comment.id)
    }

    @Transactional
    fun delete(id: Long, principal: AuthPrincipal): CommentTreeResponse {
        val comment = require(id)
        if (comment.authorId != principal.userId && !canModerate(principal)) {
            throw ApiException(ErrorCode.FORBIDDEN)
        }
        // **자식까지 지우지 않는다.** 지우면 남의 글이 함께 사라진다.
        comment.delete()
        return findTree(comment.postId, principal, around = comment.id)
    }

    fun countOf(postId: Long): Long = commentRepository.countByPostIdAndDeletedAtIsNull(postId)

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

}
