package codekr.api.board.comment

import codekr.api.config.security.PublicApi
import codekr.api.config.security.AuthenticatedApi
import codekr.api.auth.security.AuthPrincipal
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 댓글 (#138).
 *
 * 쓰기·수정·삭제 응답이 **트리 전체**다. 한 건만 돌려주면 화면이 트리의 어디에
 * 끼워 넣을지 다시 계산해야 하고, 그 규칙이 서버와 갈라진다.
 */
@RestController
@RequestMapping("/api/v1")
class CommentController(private val commentService: CommentService) {

    @PublicApi
    @GetMapping("/posts/{postId}/comments")
    fun findAll(@PathVariable postId: Long, principal: AuthPrincipal?): List<CommentResponse> =
        commentService.findTree(postId, principal)

    /** 익명 댓글을 받지 않는다 — 로그인 필수다. */
    @AuthenticatedApi
    @PostMapping("/posts/{postId}/comments")
    fun create(
        @PathVariable postId: Long,
        @RequestBody @Valid request: CommentUpsertRequest,
        principal: AuthPrincipal,
    ): List<CommentResponse> = commentService.create(postId, principal, request)

    @AuthenticatedApi
    @PutMapping("/comments/{id}")
    fun update(
        @PathVariable id: Long,
        @RequestBody @Valid request: CommentUpsertRequest,
        principal: AuthPrincipal,
    ): List<CommentResponse> = commentService.update(id, principal, request)

    @AuthenticatedApi
    @DeleteMapping("/comments/{id}")
    fun delete(@PathVariable id: Long, principal: AuthPrincipal): List<CommentResponse> =
        commentService.delete(id, principal)
}
