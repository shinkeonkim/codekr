package codekr.api.contest.repository

import codekr.api.contest.entity.Contest
import codekr.api.contest.entity.ContestStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface ContestRepository : JpaRepository<Contest, Long> {

    fun findByIdAndDeletedAtIsNull(id: Long): Contest?

    fun findBySlugAndDeletedAtIsNull(slug: String): Contest?

    fun existsBySlugAndDeletedAtIsNull(slug: String): Boolean

    /** 목록. 준비 중인 대회는 어드민만 본다. */
    fun findByStatusNotAndDeletedAtIsNullOrderByStartsAtDesc(
        status: ContestStatus,
        pageable: Pageable,
    ): Page<Contest>

    fun findByDeletedAtIsNullOrderByStartsAtDesc(pageable: Pageable): Page<Contest>
}
