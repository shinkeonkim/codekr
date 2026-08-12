package codekr.api.contest.scoreboard

import codekr.api.config.security.PublicApi
import codekr.api.auth.security.AuthPrincipal
import codekr.api.user.entity.UserRole
import org.springframework.security.access.hierarchicalroles.RoleHierarchy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 대회 순위표 (#63). 공개다 — 관전자도 본다.
 *
 * 어드민의 "실제 순위 보기" 는 **같은 경로에 인자 하나**다. 화면을 둘로 나누면 어느 쪽을
 * 보고 있는지 헷갈려 잘못된 판단을 한다 (#86).
 */
@RestController
@RequestMapping("/api/v1/contests/{slug}/scoreboard")
class ScoreboardController(
    private val scoreboardService: ScoreboardService,
    private val roleHierarchy: RoleHierarchy,
) {

    @PublicApi
    @GetMapping
    fun of(
        @PathVariable slug: String,
        @RequestParam(defaultValue = "false") actual: Boolean,
        principal: AuthPrincipal?,
    ): ScoreboardResponse {
        // 동결을 넘겨 보는 것은 대회 관리자만. 화면이 물어도 권한이 없으면 참가자와 같은 것을 본다.
        return scoreboardService.of(slug, actual && canSeeActual(principal))
    }

    /**
     * 위계는 `SecurityConfig` 의 [RoleHierarchy] 한 곳에만 있다.
     *
     * 여기서 "SUPERUSER 도 된다" 를 다시 적으면, 위계를 고칠 때 이 파일을 같이 고쳐야
     * 하는 것을 아무도 기억하지 못한다.
     */
    private fun canSeeActual(principal: AuthPrincipal?): Boolean {
        val authorities = principal?.roles?.map { SimpleGrantedAuthority("ROLE_" + it.name) }
            ?: return false
        return roleHierarchy.getReachableGrantedAuthorities(authorities)
            .any { it.authority == "ROLE_" + UserRole.CONTEST_MANAGER.name }
    }
}
