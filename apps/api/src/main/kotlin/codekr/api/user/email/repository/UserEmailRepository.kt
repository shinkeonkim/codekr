package codekr.api.user.email.repository

import codekr.api.user.email.entity.UserEmail
import org.springframework.data.jpa.repository.JpaRepository

/** 확인한 추가 메일 주소 (#396). */
interface UserEmailRepository : JpaRepository<UserEmail, Long> {
    fun findByUserIdOrderByIdAsc(userId: Long): List<UserEmail>
    fun existsByEmail(email: String): Boolean
    fun deleteByUserId(userId: Long)
}
