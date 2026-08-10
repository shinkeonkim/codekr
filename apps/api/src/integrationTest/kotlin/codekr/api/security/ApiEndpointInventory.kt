package codekr.api.security

/**
 * 이 API 가 노출하는 **모든** 엔드포인트와 그 접근 수준.
 *
 * 왜 목록을 손으로 적는가: 권한 누락은 테스트가 없으면 발견되지 않는다. 정상 흐름은
 * 늘 권한 있는 계정으로만 눌러 보기 때문이다. 실제 매핑과 이 목록을 맞춰 보면,
 * **엔드포인트를 추가하고 여기 적지 않는 순간 테스트가 깨진다** — 그때 "이건 누가 쓰는가"를
 * 한 번은 답하게 된다 (#71).
 */
enum class Access {
    /** 토큰 없이 누구나. */
    PUBLIC,

    /** 로그인한 사용자면 누구나. 자원 소유권 검사는 서비스가 따로 한다. */
    AUTHENTICATED,

    /** ADMIN 역할만. */
    ADMIN,
}

data class Endpoint(val method: String, val pattern: String, val access: Access) {
    override fun toString() = "$method $pattern (${access.name})"
}

object ApiEndpointInventory {

    val ALL: List<Endpoint> = listOf(
        // --- 인증 ---
        Endpoint("POST", "/api/v1/auth/signup", Access.PUBLIC),
        Endpoint("POST", "/api/v1/auth/login", Access.PUBLIC),
        Endpoint("POST", "/api/v1/auth/refresh", Access.PUBLIC),
        Endpoint("GET", "/api/v1/auth/me", Access.AUTHENTICATED),

        // --- 문제 (읽기는 공개) ---
        Endpoint("GET", "/api/v1/problems", Access.PUBLIC),
        Endpoint("GET", "/api/v1/problems/{slug}", Access.PUBLIC),
        Endpoint("GET", "/api/v1/runtimes", Access.PUBLIC),

        // --- 제출 ---
        Endpoint("POST", "/api/v1/problems/{slug}/run", Access.AUTHENTICATED),
        Endpoint("POST", "/api/v1/problems/{slug}/submissions", Access.AUTHENTICATED),
        Endpoint("GET", "/api/v1/submissions", Access.AUTHENTICATED),
        Endpoint("GET", "/api/v1/submissions/explore", Access.AUTHENTICATED),
        Endpoint("GET", "/api/v1/submissions/{id}", Access.AUTHENTICATED),
        Endpoint("PATCH", "/api/v1/submissions/{id}/visibility", Access.AUTHENTICATED),

        // --- 활동·프로필 ---
        Endpoint("GET", "/api/v1/users/me/activity", Access.AUTHENTICATED),
        Endpoint("GET", "/api/v1/users/{nickname}", Access.AUTHENTICATED),

        // --- 어드민 ---
        Endpoint("GET", "/api/v1/admin/problems", Access.ADMIN),
        Endpoint("POST", "/api/v1/admin/problems", Access.ADMIN),
        Endpoint("GET", "/api/v1/admin/problems/{id}", Access.ADMIN),
        Endpoint("PUT", "/api/v1/admin/problems/{id}", Access.ADMIN),
        Endpoint("DELETE", "/api/v1/admin/problems/{id}", Access.ADMIN),
        Endpoint("POST", "/api/v1/admin/problems/{id}/verify", Access.ADMIN),
        Endpoint("GET", "/api/v1/admin/queues", Access.ADMIN),
        Endpoint("GET", "/api/v1/admin/executors", Access.ADMIN),
        Endpoint("POST", "/api/v1/admin/executors/scale", Access.ADMIN),
        Endpoint("POST", "/api/v1/admin/retention/cleanup", Access.ADMIN),
    )

    /** 인증이 필요한(=비공개) 엔드포인트. */
    val PROTECTED: List<Endpoint> = ALL.filter { it.access != Access.PUBLIC }

    val ADMIN_ONLY: List<Endpoint> = ALL.filter { it.access == Access.ADMIN }
}
