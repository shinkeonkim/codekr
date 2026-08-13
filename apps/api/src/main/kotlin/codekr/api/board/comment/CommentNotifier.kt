package codekr.api.board.comment

import codekr.api.board.mention.Mentions
import codekr.api.board.repository.PostRepository
import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.notification.entity.NotificationCategory
import codekr.api.notification.service.NotificationService
import codekr.api.user.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 댓글이 만드는 알림 (#212, #214).
 *
 * 서비스에서 떼어 낸 이유는 길이만이 아니다 — **알림은 댓글 저장의 곁가지**라,
 * 실패가 저장을 되돌리면 안 된다. 그 규칙이 한 곳에 모여 있어야 지켜진다.
 */
@Component
class CommentNotifier(
    private val notificationService: NotificationService,
    private val postRepository: PostRepository,
    private val commentRepository: CommentRepository,
    private val userRepository: UserRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun onCreated(comment: Comment, writerId: Long) {
        notifyTarget(comment, writerId)
        notifyMentioned(comment, writerId, before = emptySet())
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

    fun onEdited(comment: Comment, writerId: Long, before: Set<Long>) {
        notifyMentioned(comment, writerId, before)
    }

    /**
     * 부른 사람에게 알린다 (#214).
     *
     * 답글 알림(#212)과 **다른 일이다** — 답글은 "내 것에 반응이 왔다" 이고 멘션은
     * "누가 나를 불렀다" 다. 내 글이 아닌 곳에서도 온다.
     */
    private fun notifyMentioned(comment: Comment, writerId: Long, before: Set<Long>) {
        runCatching {
            val fresh = Mentions.idsIn(comment.body)
                .filter { it != writerId && it !in before }
            if (fresh.isEmpty()) return

            val post = postRepository.findByIdAndDeletedAtIsNull(comment.postId) ?: return
            val targets = userRepository.findAllById(fresh).filterNot { it.isWithdrawn }.map { it.id }

            notificationService.notifyAll(
                userIds = targets,
                category = NotificationCategory.COMMENT,
                title = "댓글에서 나를 불렀습니다",
                body = post.title,
                link = { "/posts/${comment.postId}#comment-${comment.id}" },
            )
        }.onFailure { log.error("멘션 알림 실패 commentId={}", comment.id, it) }
    }

    /** 한 댓글에서 부를 수 있는 인원 상한 (#214). 알림을 끌 수 없으므로(#199) 필요하다. */
    fun requireMentionLimit(body: String) {
        val count = Mentions.idsIn(body).size
        if (count > Mentions.MAX_PER_BODY) {
            throw ApiException(
                ErrorCode.VALIDATION_ERROR,
                "한 번에 ${Mentions.MAX_PER_BODY}명까지 부를 수 있습니다.",
            )
        }
    }

}
