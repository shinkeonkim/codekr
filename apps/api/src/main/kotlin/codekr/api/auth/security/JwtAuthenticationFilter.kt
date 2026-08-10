package codekr.api.auth.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

private const val BEARER_PREFIX = "Bearer "

/**
 * `Authorization: Bearer <token>` 헤더를 SecurityContext 인증으로 바꾼다.
 * 토큰이 없거나 유효하지 않으면 인증을 설정하지 않고 그대로 통과시킨다 —
 * 접근 거부 여부는 뒤의 인가 규칙이 결정한다.
 */
@Component
class JwtAuthenticationFilter(private val tokenProvider: JwtTokenProvider) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        resolveToken(request)
            ?.let { tokenProvider.parse(it, TokenType.ACCESS) }
            ?.let { principal ->
                SecurityContextHolder.getContext().authentication = toAuthentication(principal)
            }
        filterChain.doFilter(request, response)
    }

    private fun resolveToken(request: HttpServletRequest): String? =
        request.getHeader("Authorization")
            ?.takeIf { it.startsWith(BEARER_PREFIX) }
            ?.removePrefix(BEARER_PREFIX)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun toAuthentication(principal: AuthPrincipal) = UsernamePasswordAuthenticationToken(
        principal,
        null,
        listOf(SimpleGrantedAuthority("ROLE_${principal.role.name}")),
    )
}
