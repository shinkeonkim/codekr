package codekr.api.contest.repository

import codekr.api.contest.entity.Contest
import codekr.api.contest.entity.ContestStatus
import codekr.api.contest.entity.ContestVisibility
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface ContestRepository : JpaRepository<Contest, Long> {

    fun findByIdAndDeletedAtIsNull(id: Long): Contest?

    fun findBySlugAndDeletedAtIsNull(slug: String): Contest?

    fun existsBySlugAndDeletedAtIsNull(slug: String): Boolean

    /** 목록. 준비 중인 대회는 어드민만 본다. */
    fun findByStatusNotAndDeletedAtIsNullOrderByStartsAtDesc(
        status: ContestStatus,
        pageable: Pageable,
    ): Page<Contest>

    /**
     * 공개 목록 (#465). **범위가 `PUBLIC` 인 것만 나온다.**
     *
     * `UNLISTED` 는 목록에 없을 뿐 주소로는 열린다 — 그것이 "링크가 있는 사람만" 의 뜻이다.
     */
    fun findByStatusNotAndVisibilityAndDeletedAtIsNullOrderByStartsAtDesc(
        status: ContestStatus,
        visibility: ContestVisibility,
        pageable: Pageable,
    ): Page<Contest>

    /**
     * 내가 등록한 대회 (#465).
     *
     * **참가한 사람은 자기 대회를 다시 찾을 수 있어야 한다.** 목록에 없는 대회에
     * 들어갔는데 어디서도 안 보이면 주소를 잃는 순간 끝이다.
     */
    @org.springframework.data.jpa.repository.Query(
        """
        SELECT c FROM Contest c
        WHERE c.deletedAt IS NULL AND c.status <> :draft
          AND EXISTS (SELECT 1 FROM ContestRegistration r WHERE r.id.contestId = c.id AND r.id.userId = :userId)
        ORDER BY c.startsAt DESC
        """,
    )
    fun findRegistered(
        userId: Long,
        draft: ContestStatus,
        pageable: Pageable,
    ): Page<Contest>

    fun findByDeletedAtIsNullOrderByStartsAtDesc(pageable: Pageable): Page<Contest>

    /** 곧 시작하는 대회. 사전 스케일 아웃이 본다 (#62). */
    fun findByStatusAndStartsAtBetween(status: ContestStatus, from: Instant, to: Instant): List<Contest>
}
