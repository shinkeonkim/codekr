package codekr.api.user.repository

import codekr.api.user.entity.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): User?
    fun existsByEmail(email: String): Boolean
    fun existsByNickname(nickname: String): Boolean
    fun findByNickname(nickname: String): User?
    /** 멘션 자동완성 (#214). 탈퇴한 사람은 부를 수 없다. */
    fun findByNicknameContainingIgnoreCaseAndWithdrawnAtIsNull(
        nickname: String,
        pageable: org.springframework.data.domain.Pageable,
    ): List<User>

}
