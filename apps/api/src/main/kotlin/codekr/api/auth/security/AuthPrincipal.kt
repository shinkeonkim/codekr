package codekr.api.auth.security

import codekr.api.user.entity.UserRole

/** 토큰에서 복원한 인증 주체. DB 조회 없이 인가 판단에 필요한 최소 정보만 담는다. */
data class AuthPrincipal(
    val userId: Long,
    val email: String,
    val role: UserRole,
) {
    val isAdmin: Boolean get() = role == UserRole.ADMIN
}
