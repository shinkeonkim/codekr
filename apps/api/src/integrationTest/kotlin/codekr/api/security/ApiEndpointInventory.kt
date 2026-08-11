package codekr.api.security

import codekr.api.user.entity.UserRole

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
        Endpoint("GET", "/api/v1/rankings", Access.PUBLIC),
        Endpoint("GET", "/api/v1/rankings/metrics", Access.PUBLIC),
        Endpoint("GET", "/api/v1/contests", Access.PUBLIC),
        Endpoint("GET", "/api/v1/contests/{slug}", Access.PUBLIC),
        Endpoint("GET", "/api/v1/contests/{slug}/scoreboard", Access.PUBLIC),
        Endpoint("GET", "/api/v1/collections/shared/{shareToken}", Access.PUBLIC),
        Endpoint("GET", "/api/v1/files/{prefix}/{name}", Access.PUBLIC),
        Endpoint("GET", "/api/v1/posts", Access.PUBLIC),
        Endpoint("GET", "/api/v1/posts/boards", Access.PUBLIC),
        Endpoint("GET", "/api/v1/posts/{id}", Access.PUBLIC),
        Endpoint("GET", "/api/v1/posts/{postId}/comments", Access.PUBLIC),
        Endpoint("POST", "/api/v1/contests/{slug}/registrations", Access.AUTHENTICATED),
        Endpoint(
            "POST",
            "/api/v1/contests/{contestSlug}/problems/{problemSlug}/submissions",
            Access.AUTHENTICATED,
        ),

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
        Endpoint("GET", "/api/v1/users/{nickname}/activity", Access.AUTHENTICATED),
        Endpoint("POST", "/api/v1/posts", Access.AUTHENTICATED),
        Endpoint("POST", "/api/v1/posts/{postId}/comments", Access.AUTHENTICATED),
        Endpoint("PUT", "/api/v1/comments/{id}", Access.AUTHENTICATED),
        Endpoint("DELETE", "/api/v1/comments/{id}", Access.AUTHENTICATED),
        Endpoint("PUT", "/api/v1/posts/{id}", Access.AUTHENTICATED),
        Endpoint("DELETE", "/api/v1/posts/{id}", Access.AUTHENTICATED),
        Endpoint("GET", "/api/v1/collections/me", Access.AUTHENTICATED),
        Endpoint("GET", "/api/v1/collections/{id}", Access.AUTHENTICATED),
        Endpoint("POST", "/api/v1/collections", Access.AUTHENTICATED),
        Endpoint("PUT", "/api/v1/collections/{id}", Access.AUTHENTICATED),
        Endpoint("DELETE", "/api/v1/collections/{id}", Access.AUTHENTICATED),
        Endpoint("PUT", "/api/v1/users/me/avatar", Access.AUTHENTICATED),
        Endpoint("DELETE", "/api/v1/users/me/avatar", Access.AUTHENTICATED),
        Endpoint("GET", "/api/v1/users/me/settings", Access.AUTHENTICATED),
        Endpoint("PATCH", "/api/v1/users/me/settings", Access.AUTHENTICATED),

        // --- 알림 (#106) ---
        Endpoint("GET", "/api/v1/notifications", Access.AUTHENTICATED),
        Endpoint("GET", "/api/v1/notifications/unread-count", Access.AUTHENTICATED),
        Endpoint("POST", "/api/v1/notifications/{id}/read", Access.AUTHENTICATED),
        Endpoint("POST", "/api/v1/notifications/read-all", Access.AUTHENTICATED),

        // --- 어드민 ---
        Endpoint("GET", "/api/v1/admin/problems", Access.ADMIN, UserRole.PROBLEM_SETTER),
        Endpoint("POST", "/api/v1/admin/problems", Access.ADMIN, UserRole.PROBLEM_SETTER),
        Endpoint("GET", "/api/v1/admin/problems/{id}", Access.ADMIN, UserRole.PROBLEM_SETTER),
        Endpoint("PUT", "/api/v1/admin/problems/{id}", Access.ADMIN, UserRole.PROBLEM_SETTER),
        Endpoint("DELETE", "/api/v1/admin/problems/{id}", Access.ADMIN, UserRole.PROBLEM_SETTER),
        Endpoint("POST", "/api/v1/admin/problems/{id}/verify", Access.ADMIN, UserRole.PROBLEM_SETTER),
        Endpoint("POST", "/api/v1/admin/problems/{id}/rejudge", Access.ADMIN, UserRole.PROBLEM_SETTER),
        Endpoint("GET", "/api/v1/admin/queues", Access.ADMIN, UserRole.ADMIN),
        Endpoint("GET", "/api/v1/admin/executors", Access.ADMIN, UserRole.ADMIN),
        Endpoint("POST", "/api/v1/admin/executors/scale", Access.ADMIN, UserRole.ADMIN),
        Endpoint("POST", "/api/v1/admin/retention/cleanup", Access.ADMIN, UserRole.ADMIN),
        // 경로 규칙에 적지 않아 안전한 기본값(최고 관리자)이 적용된다.
        Endpoint("GET", "/api/v1/admin/contests", Access.ADMIN, UserRole.CONTEST_MANAGER),
        Endpoint("GET", "/api/v1/admin/contests/{id}", Access.ADMIN, UserRole.CONTEST_MANAGER),
        Endpoint("POST", "/api/v1/admin/contests", Access.ADMIN, UserRole.CONTEST_MANAGER),
        Endpoint("PUT", "/api/v1/admin/contests/{id}", Access.ADMIN, UserRole.CONTEST_MANAGER),
        Endpoint("PUT", "/api/v1/admin/contests/{id}/status", Access.ADMIN, UserRole.CONTEST_MANAGER),
        Endpoint("POST", "/api/v1/admin/contests/{id}/unfreeze", Access.ADMIN, UserRole.CONTEST_MANAGER),
        Endpoint(
            "PUT",
            "/api/v1/admin/contests/{id}/problems/{problemId}/exclusion",
            Access.ADMIN,
            UserRole.CONTEST_MANAGER,
        ),
        Endpoint("DELETE", "/api/v1/admin/contests/{id}", Access.ADMIN, UserRole.CONTEST_MANAGER),
        Endpoint("PUT", "/api/v1/admin/users/{id}/roles", Access.ADMIN, UserRole.SUPERUSER),
        Endpoint("POST", "/api/v1/admin/users/{userId}/activity/recompute", Access.ADMIN, UserRole.SUPERUSER),
    )

    /** 인증이 필요한(=비공개) 엔드포인트. */
    val PROTECTED: List<Endpoint> = ALL.filter { it.access != Access.PUBLIC }

    val ADMIN_ONLY: List<Endpoint> = ALL.filter { it.access == Access.ADMIN }
}
