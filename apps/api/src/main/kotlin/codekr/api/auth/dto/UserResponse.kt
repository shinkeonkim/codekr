package codekr.api.auth.dto

import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole

data class UserResponse(
    val id: Long,
    val email: String,
    val nickname: String,
    val role: UserRole,
) {
    companion object {
        fun from(user: User) = UserResponse(user.id, user.email, user.nickname, user.role)
    }
}
