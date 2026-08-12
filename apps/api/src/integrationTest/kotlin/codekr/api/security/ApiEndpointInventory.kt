package codekr.api.security

import codekr.api.config.security.ApiAccessLevel
import codekr.api.config.security.ApiAccessScanner
import codekr.api.user.entity.UserRole

/**
 * 이 API 가 노출하는 **모든** 엔드포인트와 그 접근 수준.
 *
 * **더 이상 손으로 적지 않는다** (#198). 컨트롤러의 선언(`@PublicApi`·`@AuthenticatedApi`·
 * `@AdminApi`)에서 끌어낸다 — 전에는 이 목록이 사본이라, 실제 규칙(`SecurityConfig` 의
 * 패턴)과 어긋나도 아무도 몰랐다. 시험이 확인한 것은 "매핑이 목록에 있는가" 였지
 * "목록이 실제 규칙과 같은가" 가 아니었다.
 *
 * 시험 자체는 남는다. 선언한 대로 **실제로 401/403 이 나오는지**는 여전히 눌러 봐야
 * 안다 — 목록이 자동으로 만들어지는 지금은 이 시험이 유일한 검증이다 (#71).
 */
enum class Access {
    /** 토큰 없이 누구나. */
    PUBLIC,

    /** 로그인한 사용자면 누구나. 자원 소유권 검사는 서비스가 따로 한다. */
    AUTHENTICATED,

    /** 특정 어드민 역할만. 어떤 역할인지는 [Endpoint.role] 에 적는다. */
    ADMIN,
}

/**
 * [role] 은 `access == ADMIN` 일 때 **반드시** 적어야 한다 (#103).
 *
 * 역할을 적지 않고는 어드민 API 를 늘릴 수 없게 만드는 것이 목적이다 —
 * 적는 순간 "이건 누가 쓰는가" 를 한 번은 답하게 된다.
 */
data class Endpoint(
    val method: String,
    val pattern: String,
    val access: Access,
    val role: UserRole? = null,
) {
    init {
        require((access == Access.ADMIN) == (role != null)) {
            "어드민 엔드포인트는 역할을 적어야 하고, 그 외에는 적으면 안 됩니다: $method $pattern"
        }
    }

    override fun toString() = "$method $pattern (${role?.name ?: access.name})"
}

object ApiEndpointInventory {

    /** 컨트롤러 선언에서 끌어낸 목록. */
    val ALL: List<Endpoint> = ApiAccessScanner.scan().map { rule ->
        when (val level = rule.level) {
            is ApiAccessLevel.Public -> Endpoint(rule.method, rule.pattern, Access.PUBLIC)
            is ApiAccessLevel.Authenticated -> Endpoint(rule.method, rule.pattern, Access.AUTHENTICATED)
            is ApiAccessLevel.Role -> Endpoint(rule.method, rule.pattern, Access.ADMIN, level.role)
        }
    }

    /** 인증이 필요한(=비공개) 엔드포인트. */
    val PROTECTED: List<Endpoint> = ALL.filter { it.access != Access.PUBLIC }

    val ADMIN_ONLY: List<Endpoint> = ALL.filter { it.access == Access.ADMIN }
}
