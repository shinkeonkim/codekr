package codekr.api.auth.dto

import codekr.api.user.avatar.AvatarService
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole

data class UserResponse(
    val id: Long,
    val email: String,
    val nickname: String,
    /** 가진 역할 전부 (#103). 화면이 무엇을 보여줄지 이것으로 정한다. */
    val roles: Set<UserRole>,
    /** 어드민 영역 진입 가능 여부. 역할 목록을 화면이 매번 해석하지 않게 함께 내린다. */
    val isAdmin: Boolean,
    /** 아바타 주소 (#116). 올리지 않았으면 null — 화면이 기본 표현을 그린다. */
    val avatarUrl: String?,
) {
    companion object {
        fun from(user: User) = UserResponse(
            user.id,
            user.email,
            user.nickname,
            user.roles,
            user.isAdmin,
            AvatarService.urlOf(user.avatarKey),
        )
    }
}
