package codekr.api.user.entity

/**
 * 탈퇴한 사용자의 표시 이름 (#140).
 *
 * **한 곳에서만 만든다.** 화면마다 문구를 따로 쓰면 어떤 곳은 "탈퇴한 회원",
 * 어떤 곳은 "(알 수 없음)" 이 되고, 읽는 사람은 서로 다른 상태로 오해한다.
 */
object WithdrawnUser {
    const val LABEL = "탈퇴한 사용자"

    /** 탈퇴했으면 표시 이름을, 아니면 원래 닉네임을 준다. */
    fun nicknameOf(user: User?): String =
        user?.takeUnless { it.isWithdrawn }?.nickname ?: LABEL

    /** 탈퇴했으면 아바타를 내리지 않는다. */
    fun avatarKeyOf(user: User?): String? = user?.takeUnless { it.isWithdrawn }?.avatarKey
}
