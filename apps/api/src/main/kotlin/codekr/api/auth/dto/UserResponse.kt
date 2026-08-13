package codekr.api.auth.dto

import codekr.api.user.avatar.AvatarService
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole

data class UserResponse(
    val id: Long,
    val email: String,
    val nickname: String,
    /** 주소가 되는 이름 (#307). 헤더의 프로필 링크가 이것을 쓴다. */
    val handle: String,
    /** 가진 역할 전부 (#103). 화면이 무엇을 보여줄지 이것으로 정한다. */
    val roles: Set<UserRole>,
    /** 어드민 영역 진입 가능 여부. 역할 목록을 화면이 매번 해석하지 않게 함께 내린다. */
    val isAdmin: Boolean,
    /** 아바타 주소 (#116). 올리지 않았으면 null — 화면이 기본 표현을 그린다. */
    val avatarUrl: String?,
    /**
     * 소개 문구 (#310). 안 썼으면 null.
     *
     * 설정 화면이 **지금 쓴 것을 보여주고 고치게** 하려면 필요하다 — 프로필 조회로
     * 대신할 수는 있지만, 그러려면 자기 닉네임으로 자기를 다시 조회해야 한다.
     */
    val bio: String?,
    /**
     * 이메일을 확인했는가 (#233).
     *
     * **화면이 안내를 띄울지 정하는 값**이라 시각이 아니라 참·거짓으로 내린다 —
     * 언제 확인했는지는 사용자에게 쓸모가 없다.
     */
    val emailVerified: Boolean,
) {
    companion object {
        fun from(user: User) = UserResponse(
            user.id,
            user.email,
            user.nickname,
            user.handle,
            user.roles,
            user.isAdmin,
            AvatarService.urlOf(user.avatarKey),
            user.bio,
            user.emailVerifiedAt != null,
        )
    }
}
