package codekr.api.user.suspension

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface UserSuspensionRepository : JpaRepository<UserSuspension, Long> {

    /**
     * 지금 효력이 있는 정지들.
     *
     * 여럿일 수 있다 — 쓰기 정지 중에 제출 정지가 더해질 수 있고, 그때 뒤엣것이
     * 앞엣것을 지워서는 안 된다.
     */
    @Query(
        """
        SELECT s FROM UserSuspension s
        WHERE s.userId = :userId
          AND s.liftedAt IS NULL
          AND (s.endsAt IS NULL OR s.endsAt > :now)
        ORDER BY s.id DESC
        """,
    )
    fun findActive(@Param("userId") userId: Long, @Param("now") now: Instant): List<UserSuspension>

    /** 목록 한 화면분을 한 번에 (#223 의 회원 목록). */
    @Query(
        """
        SELECT s FROM UserSuspension s
        WHERE s.userId IN :userIds
          AND s.liftedAt IS NULL
          AND (s.endsAt IS NULL OR s.endsAt > :now)
        """,
    )
    fun findActiveIn(@Param("userIds") userIds: Collection<Long>, @Param("now") now: Instant): List<UserSuspension>
}
