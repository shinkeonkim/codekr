package codekr.api.user.repository

import codekr.api.user.entity.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): User?
    fun existsByEmail(email: String): Boolean
    fun existsByNickname(nickname: String): Boolean
    fun findByNickname(nickname: String): User?

    /** 주소로 찾는다 (#307). **바뀌지 않는 값**이라 링크가 끊기지 않는다. */
    fun findByHandle(handle: String): User?

    fun existsByHandle(handle: String): Boolean
    /** 멘션 자동완성 (#214). 탈퇴한 사람은 부를 수 없다. */
    fun findByNicknameContainingIgnoreCaseAndWithdrawnAtIsNull(
        nickname: String,
        pageable: org.springframework.data.domain.Pageable,
    ): List<User>

}
