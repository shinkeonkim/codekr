package codekr.api.board.admin

import codekr.api.audit.entity.AdminAction
import codekr.api.audit.service.AdminAuditService
import codekr.api.auth.security.AuthPrincipal
import codekr.api.board.comment.CommentService
import codekr.api.board.service.PostService
import codekr.api.common.dto.PageResponse
import codekr.api.config.security.AdminApi
import codekr.api.user.entity.UserRole
import codekr.api.user.entity.WithdrawnUser
import codekr.api.user.repository.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/**
 * 게시판 관리 (#336).
 *
 * **"지울 수 있다" 와 "지울 것을 찾을 수 있다" 는 다르다.** 지금까지 운영자가 문제
 * 있는 글을 지우려면 **그 글을 우연히 보고 있어야** 했다 — 게시판이 셋이고 댓글은
 * 글 안에 들어가야만 보였다.
 */
@RestController
@RequestMapping("/api/v1/admin/board")
class AdminBoardController(private val service: AdminBoardService) {

    /**
     * 글 목록 — **게시판 구분 없이 최근 순으로 한 곳에서.**
     *
     * 문제별 질문(#139)도 `posts` 에 있으므로 같은 목록에 온다. 화면이 탭으로 나눈다 —
     * 표를 둘로 두면 "최근에 올라온 것" 을 볼 수 없다.
     */
    @AdminApi(UserRole.BOARD_MANAGER)
    @GetMapping("/posts")
    fun posts(
        @RequestParam(required = false) board: String?,
        @RequestParam(required = false) authorNickname: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<AdminPostRow> = service.posts(board, authorNickname, page, size)

    /** 댓글 목록. **글 안에 숨어 있으면 안 된다** — 어디에 무엇이 달렸는지 훑는 자리다. */
    @AdminApi(UserRole.BOARD_MANAGER)
    @GetMapping("/comments")
    fun comments(
        @RequestParam(required = false) authorNickname: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<AdminCommentRow> = service.comments(authorNickname, page, size)

    @AdminApi(UserRole.BOARD_MANAGER)
    @DeleteMapping("/posts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deletePost(
        @PathVariable id: Long,
        @RequestParam reason: String?,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ) = service.deletePost(id, principal, reason)

    @AdminApi(UserRole.BOARD_MANAGER)
    @DeleteMapping("/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteComment(
        @PathVariable id: Long,
        @RequestParam reason: String?,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ) = service.deleteComment(id, principal, reason)
}

data class AdminPostRow(
    val id: Long,
    val board: String,
    val title: String,
    val authorNickname: String,
    /** 문제별 질문이면 그 문제 (#139). 커뮤니티 글이면 null. */
    val problemId: Long?,
    val commentCount: Int,
    val createdAt: Instant,
)

data class AdminCommentRow(
    val id: Long,
    val postId: Long,
    val postTitle: String,
    val authorNickname: String,
    /** 본문 앞부분. 목록에서 무엇인지 알아볼 만큼만 — 전문은 글에서 본다. */
    val excerpt: String,
    val createdAt: Instant,
)

@Service
class AdminBoardService(
    private val jdbcClient: JdbcClient,
    private val postService: PostService,
    private val commentService: CommentService,
    private val users: UserRepository,
    private val auditService: AdminAuditService,
) {

    fun posts(board: String?, authorNickname: String?, page: Int, size: Int): PageResponse<AdminPostRow> {
        val pageable = PageRequest.of(maxOf(page, 0), size.coerceIn(1, 50))
        val rows = jdbcClient.sql(
            """
            SELECT p.id, p.board, p.title, p.problem_id, p.created_at,
                   u.nickname, u.withdrawn_at,
                   (SELECT count(*) FROM comments c WHERE c.post_id = p.id AND c.deleted_at IS NULL) AS comment_count
            FROM posts p
            JOIN users u ON u.id = p.author_id
            WHERE p.deleted_at IS NULL
              AND (CAST(:board AS text) IS NULL OR p.board = :board)
              AND (CAST(:nickname AS text) IS NULL OR u.nickname ILIKE '%' || :nickname || '%')
            ORDER BY p.id DESC
            LIMIT :limit OFFSET :offset
            """,
        )
            .param("board", board)
            .param("nickname", authorNickname)
            .param("limit", pageable.pageSize)
            .param("offset", pageable.offset)
            .query { rs, _ ->
                AdminPostRow(
                    id = rs.getLong("id"),
                    board = rs.getString("board"),
                    title = rs.getString("title"),
                    // 탈퇴한 사람은 다른 곳과 같은 규칙으로 가린다 (#140).
                    authorNickname = if (rs.getTimestamp("withdrawn_at") != null) {
                        WithdrawnUser.LABEL
                    } else {
                        rs.getString("nickname")
                    },
                    problemId = rs.getObject("problem_id")?.let { (it as Number).toLong() },
                    commentCount = rs.getInt("comment_count"),
                    createdAt = rs.getTimestamp("created_at").toInstant(),
                )
            }
            .list()

        val total = jdbcClient.sql(
            """
            SELECT count(*) FROM posts p JOIN users u ON u.id = p.author_id
            WHERE p.deleted_at IS NULL
              AND (CAST(:board AS text) IS NULL OR p.board = :board)
              AND (CAST(:nickname AS text) IS NULL OR u.nickname ILIKE '%' || :nickname || '%')
            """,
        )
            .param("board", board).param("nickname", authorNickname)
            .query(Long::class.java).single()

        return PageResponse.from(org.springframework.data.domain.PageImpl(rows, pageable, total))
    }

    fun comments(authorNickname: String?, page: Int, size: Int): PageResponse<AdminCommentRow> {
        val pageable = PageRequest.of(maxOf(page, 0), size.coerceIn(1, 50))
        val rows = jdbcClient.sql(
            """
            SELECT c.id, c.post_id, c.body, c.created_at, p.title, u.nickname, u.withdrawn_at
            FROM comments c
            JOIN posts p ON p.id = c.post_id
            JOIN users u ON u.id = c.author_id
            WHERE c.deleted_at IS NULL
              AND (CAST(:nickname AS text) IS NULL OR u.nickname ILIKE '%' || :nickname || '%')
            ORDER BY c.id DESC
            LIMIT :limit OFFSET :offset
            """,
        )
            .param("nickname", authorNickname)
            .param("limit", pageable.pageSize)
            .param("offset", pageable.offset)
            .query { rs, _ ->
                AdminCommentRow(
                    id = rs.getLong("id"),
                    postId = rs.getLong("post_id"),
                    postTitle = rs.getString("title"),
                    authorNickname = if (rs.getTimestamp("withdrawn_at") != null) {
                        WithdrawnUser.LABEL
                    } else {
                        rs.getString("nickname")
                    },
                    excerpt = rs.getString("body").take(EXCERPT_LENGTH),
                    createdAt = rs.getTimestamp("created_at").toInstant(),
                )
            }
            .list()

        val total = jdbcClient.sql(
            """
            SELECT count(*) FROM comments c JOIN users u ON u.id = c.author_id
            WHERE c.deleted_at IS NULL
              AND (CAST(:nickname AS text) IS NULL OR u.nickname ILIKE '%' || :nickname || '%')
            """,
        )
            .param("nickname", authorNickname)
            .query(Long::class.java).single()

        return PageResponse.from(org.springframework.data.domain.PageImpl(rows, pageable, total))
    }

    /**
     * 글을 내린다.
     *
     * **권한 판정은 `PostService` 의 것을 그대로 쓴다** — 여기서 다시 적으면 두 곳이
     * 갈린다. 소프트 삭제인 것도 그대로다(ADR-0007) — 지워도 스레드가 무너지지 않는다.
     */
    @Transactional
    fun deletePost(id: Long, principal: AuthPrincipal, reason: String?) {
        val row = jdbcClient.sql("SELECT title, author_id FROM posts WHERE id = :id")
            .param("id", id)
            .query { rs, _ -> rs.getString("title") to rs.getLong("author_id") }
            .single()

        postService.delete(id, principal)
        record(principal.userId, row.second, AdminAction.POST_DELETE, reason, row.first)
    }

    @Transactional
    fun deleteComment(id: Long, principal: AuthPrincipal, reason: String?) {
        val row = jdbcClient.sql("SELECT body, author_id FROM comments WHERE id = :id")
            .param("id", id)
            .query { rs, _ -> rs.getString("body") to rs.getLong("author_id") }
            .single()

        commentService.delete(id, principal)
        record(principal.userId, row.second, AdminAction.COMMENT_DELETE, reason, row.first.take(EXCERPT_LENGTH))
    }

    /** **남의 글을 지우는 일이므로 기록이 없으면 안 된다** (#225). */
    private fun record(actorId: Long, authorId: Long, action: AdminAction, reason: String?, detail: String) {
        auditService.record(
            actorId = actorId,
            action = action,
            targetId = authorId,
            targetLabel = users.findById(authorId).map { it.nickname }.orElse(null),
            reason = reason,
            detail = detail,
        )
    }

    private companion object {
        const val EXCERPT_LENGTH = 120
    }
}
