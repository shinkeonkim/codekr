package codekr.api.problem.vote

import codekr.api.auth.security.AuthPrincipal
import codekr.api.config.security.AuthenticatedApi
import codekr.api.config.security.PublicApi
import codekr.api.problem.service.ProblemService
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 난이도 투표 (#477).
 *
 * **푼 사람만 투표한다.** 안 풀고 매기는 것은 뜻이 약하고, 못 푼 사람의 체감은
 * 정답률(#84)이 이미 말한다.
 */
@RestController
@RequestMapping("/api/v1/problems/{slug}/difficulty-vote")
class DifficultyVoteController(
    private val voteService: DifficultyVoteService,
    private val problemService: ProblemService,
) {

    /**
     * 지금 상태. **분포는 내가 투표한 뒤에만 온다** — 먼저 보면 뒤에 오는 사람이 끌려간다.
     *
     * 로그인 없이도 열린다. 그때는 "투표할 수 없음" 만 알려 준다 — 로그인 화면으로
     * 튕기면 문제 화면이 통째로 막힌다.
     */
    @PublicApi
    @GetMapping
    fun summary(@PathVariable slug: String, principal: AuthPrincipal?): DifficultyVoteResponse =
        voteService.summary(problemService.requirePublished(slug).id, principal?.userId)

    @AuthenticatedApi
    @PostMapping
    fun vote(
        @PathVariable slug: String,
        @RequestBody request: DifficultyVoteRequest,
        principal: AuthPrincipal,
    ): DifficultyVoteResponse =
        voteService.vote(problemService.requirePublished(slug).id, principal.userId, request.level)
}

data class DifficultyVoteRequest(
    @field:Min(1) @field:Max(30) val level: Int,
)
