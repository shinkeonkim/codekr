package codekr.api.board.controller

import codekr.api.auth.security.AuthPrincipal
import codekr.api.board.dto.BoardOption
import codekr.api.board.dto.PostDetailResponse
import codekr.api.board.dto.PostSummaryResponse
import codekr.api.board.dto.PostUpsertRequest
import codekr.api.board.entity.Board
import codekr.api.board.service.PostService
import codekr.api.common.dto.PageResponse
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 게시글 (#137).
 *
 * 읽기는 공개다 — 로그인해야 읽을 수 있으면 검색으로 들어온 사람이 아무것도 볼 수 없다.
 * 쓰기는 로그인이 필요하다.
 */
@RestController
@RequestMapping("/api/v1/posts")
class PostController(private val postService: PostService) {

    @GetMapping
    fun findAll(
        @RequestParam(required = false) board: Board?,
        @RequestParam(required = false) q: String?,
        @PageableDefault(size = 20) pageable: Pageable,
        principal: AuthPrincipal?,
    ): PageResponse<PostSummaryResponse> = postService.findPage(board, q, pageable)

    /** 게시판 목록. **화면이 하드코딩하지 않게 서버가 알려준다** — 쓸 수 있는지도 함께. */
    @GetMapping("/boards")
    fun boards(principal: AuthPrincipal?): List<BoardOption> = postService.boards(principal)

    @GetMapping("/{id}")
    fun findOne(@PathVariable id: Long, principal: AuthPrincipal?): PostDetailResponse =
        postService.findDetail(id, principal)

    @PostMapping
    fun create(
        @RequestBody @Valid request: PostUpsertRequest,
        principal: AuthPrincipal,
    ): ResponseEntity<PostDetailResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(postService.create(principal, request))

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @RequestBody @Valid request: PostUpsertRequest,
        principal: AuthPrincipal,
    ): PostDetailResponse = postService.update(id, principal, request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long, principal: AuthPrincipal) = postService.delete(id, principal)
}
