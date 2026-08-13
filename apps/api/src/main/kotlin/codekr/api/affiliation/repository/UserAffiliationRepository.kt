package codekr.api.affiliation.repository

import codekr.api.affiliation.entity.UserAffiliation
import org.springframework.data.jpa.repository.JpaRepository

/** 사람에게 붙은 소속 (#398). */
interface UserAffiliationRepository : JpaRepository<UserAffiliation, Long> {
    fun findByUserIdOrderByIdAsc(userId: Long): List<UserAffiliation>
    fun existsByUserIdAndAffiliationId(userId: Long, affiliationId: Long): Boolean
    fun deleteByUserId(userId: Long)
}
