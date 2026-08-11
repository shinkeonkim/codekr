package codekr.api.contest.controller

import codekr.api.auth.security.AuthPrincipal
import codekr.api.common.dto.PageResponse
import codekr.api.contest.dto.ContestDetailResponse
import codekr.api.contest.dto.ContestSummaryResponse
import codekr.api.contest.service.ContestService
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** 대회 조회와 참가 (#61). 목록·상세는 공개, 참가는 로그인이 필요하다. */
@RestController
@RequestMapping("/api/v1/contests")
class ContestController(private val contestService: ContestService) {

    @GetMapping
    fun findAll(
        @PageableDefault(size = 20) pageable: Pageable,
    ): PageResponse<ContestSummaryResponse> = contestService.findAll(pageable)

    @GetMapping("/{slug}")
    fun findDetail(
        @PathVariable slug: String,
        principal: AuthPrincipal?,
    ): ContestDetailResponse = contestService.findDetail(slug, principal?.userId)

    @PostMapping("/{slug}/registrations")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun register(
        @PathVariable slug: String,
        principal: AuthPrincipal,
    ) = contestService.register(slug, principal.userId)
}
