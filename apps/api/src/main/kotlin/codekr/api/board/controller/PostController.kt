package codekr.api.board.controller

import codekr.api.config.security.PublicApi
import codekr.api.config.security.AuthenticatedApi
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

    @PublicApi
    @GetMapping
    fun findAll(
        @RequestParam(required = false) board: Board?,
        @RequestParam(required = false) q: String?,
        @PageableDefault(size = 20) pageable: Pageable,
        principal: AuthPrincipal?,
    ): PageResponse<PostSummaryResponse> = postService.findPage(board, q, pageable)

    /** 게시판 목록. **화면이 하드코딩하지 않게 서버가 알려준다** — 쓸 수 있는지도 함께. */
    @PublicApi
    @GetMapping("/boards")
    fun boards(principal: AuthPrincipal?): List<BoardOption> = postService.boards(principal)

    /**
     * 한 문제에 붙은 질문 (#139).
     *
     * 문제 상세의 질문 탭이 쓴다. 커뮤니티 목록에도 **함께 보인다** —
     * 분리하면 질문이 두 곳에 흩어지고, "다음 사람이 먼저 읽고 간다" 는 목적이 약해진다.
     */
    @PublicApi
    @GetMapping("/by-problem/{problemId}")
    fun findByProblem(
        @PathVariable problemId: Long,
        @PageableDefault(size = 20) pageable: Pageable,
    ): PageResponse<PostSummaryResponse> = postService.findByProblem(problemId, pageable)

    @PublicApi
    @GetMapping("/{id}")
    fun findOne(@PathVariable id: Long, principal: AuthPrincipal?): PostDetailResponse =
        postService.findDetail(id, principal)

    @AuthenticatedApi
    @PostMapping
    fun create(
        @RequestBody @Valid request: PostUpsertRequest,
        principal: AuthPrincipal,
    ): ResponseEntity<PostDetailResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(postService.create(principal, request))

    @AuthenticatedApi
    @PutMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @RequestBody @Valid request: PostUpsertRequest,
        principal: AuthPrincipal,
    ): PostDetailResponse = postService.update(id, principal, request)

    @AuthenticatedApi
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long, principal: AuthPrincipal) = postService.delete(id, principal)
}
