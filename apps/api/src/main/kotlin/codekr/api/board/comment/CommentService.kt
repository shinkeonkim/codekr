package codekr.api.board.comment

import codekr.api.auth.security.AuthPrincipal
import codekr.api.board.repository.PostRepository
import codekr.api.common.error.ApiException
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
    private val notifier: CommentNotifier,
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
        val assembler = CommentTreeAssembler(
            comments,
            authors,
            principal,
            ancestorsOf(comments, around),
            mentionLabels(comments),
        )

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
        val assembler = CommentTreeAssembler(comments, authors, principal, emptySet(), mentionLabels(comments))

        val children = assembler.build(commentId, depth = 0, limit = CommentTreeAssembler.CHILD_PAGE, after = after)
        return CommentTreeResponse(
            comments = children,
            totalCount = assembler.visibleCount(),
            remainingTop = assembler.remaining(commentId, children.size, after),
        )
    }

    /**
     * 본문이 부른 사람들의 이름표를 **한 번에** 읽는다 (#214).
     *
     * 댓글마다 조회하면 트리 크기만큼 질의가 나간다 — #138 이 트리를 한 번의 조회로
     * 만들기로 한 것과 같은 이유다.
     */
    private fun mentionLabels(comments: List<Comment>): Map<Long, codekr.api.board.mention.MentionResponse> {
        val ids = comments.flatMap { codekr.api.board.mention.Mentions.idsIn(it.body) }.distinct()
        if (ids.isEmpty()) return emptyMap()
        return userRepository.findAllById(ids).associate { it.id to codekr.api.board.mention.Mentions.of(it) }
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

        notifier.requireMentionLimit(request.body)
        val saved = commentRepository.save(Comment(postId, request.parentId, principal.userId, request.body))
        notifier.onCreated(saved, principal.userId)
        // 방금 쓴 댓글이 접힌 자리에 들어가면 쓴 사람이 자기 글을 못 본다.
        return findTree(postId, principal, around = saved.id)
    }

    @Transactional
    fun update(id: Long, principal: AuthPrincipal, request: CommentUpsertRequest): CommentTreeResponse {
        val comment = require(id)
        // 어드민도 남의 댓글을 고칠 수는 없다 (#137 과 같은 이유).
        if (comment.authorId != principal.userId) throw ApiException(ErrorCode.FORBIDDEN)
        notifier.requireMentionLimit(request.body)
        // **고쳐서 새로 부른 사람에게는 알린다** (#214). 이미 불린 사람에게 다시 보내면
        // 오타를 고칠 때마다 알림이 간다.
        val before = codekr.api.board.mention.Mentions.idsIn(comment.body).toSet()
        comment.edit(request.body)
        notifier.onEdited(comment, principal.userId, before)
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

}
