package codekr.api.auth.security

import codekr.api.user.entity.UserRole

/** 토큰에서 복원한 인증 주체. DB 조회 없이 인가 판단에 필요한 최소 정보만 담는다. */
data class AuthPrincipal(
    val userId: Long,
    val email: String,
    val roles: Set<UserRole>,
) {
    /**
     * 어드민 영역에 들어올 수 있는가 (#103).
     *
     * 제출 소스 공개 범위(#33)처럼 "관리자면 볼 수 있다" 를 판단할 때 쓴다.
     * 어느 어드민 API 를 부를 수 있는지는 이것과 별개로 역할마다 다르다.
     */
    val isAdmin: Boolean get() = roles.any { it in UserRole.ADMIN_AREA }

    fun has(role: UserRole): Boolean = role in roles
}
