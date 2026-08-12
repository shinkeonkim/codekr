package codekr.api.user.repository

import codekr.api.user.entity.QUser
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import com.querydsl.core.BooleanBuilder
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant

/** 어드민 회원 검색 (#223). */
@Repository
class AdminUserSearchRepository(
    private val queryFactory: JPAQueryFactory,
    private val jdbcClient: JdbcClient,
) {

    fun search(keyword: String?, role: UserRole?, includeWithdrawn: Boolean, pageable: Pageable): Page<User> {
        val user = QUser.user
        val predicate = BooleanBuilder().apply {
            // **탈퇴한 회원은 기본으로 뺀다.** 대부분의 조회는 살아 있는 사람을 찾는 일이다.
            if (!includeWithdrawn) and(user.withdrawnAt.isNull)
            keyword?.let {
                and(user.nickname.containsIgnoreCase(it).or(user.email.containsIgnoreCase(it)))
            }
            /*
                역할은 컬렉션이라 조인이 아니라 존재 검사로 건다.

                조인하면 역할이 둘인 사람이 목록에 두 번 나온다 — `distinct` 로 덮을 수는
                있지만 그때는 `count` 와 어긋난다.
            */
            role?.let { and(user.roleSet.contains(it)) }
        }

        val content = queryFactory
            .selectFrom(user)
            .where(predicate)
            // 최근 가입 순. 어드민이 찾는 사람은 대개 최근에 들어온 사람이다.
            .orderBy(user.createdAt.desc(), user.id.desc())
            .offset(pageable.offset)
            .limit(pageable.pageSize.toLong())
            .fetch()

        val total = queryFactory.select(user.count()).from(user).where(predicate).fetchOne() ?: 0L
        return PageImpl(content, pageable, total)
    }

    /**
     * 제출 활동 요약.
     *
     * 점수·푼 문제 수는 랭킹 표에서 온다 — **여기서 다시 세지 않는다.** 세는 규칙이 두
     * 곳에 생기면 프로필과 어드민 화면의 숫자가 갈린다 (#269 에서 겪은 것).
     */
    fun activityOf(userId: Long): UserActivitySummary =
        jdbcClient.sql(
            """
            SELECT count(*)::int AS submission_count, max(created_at) AS last_submitted_at
            FROM submissions
            WHERE user_id = :userId AND deleted_at IS NULL AND kind = 'USER'
            """,
        )
            .param("userId", userId)
            .query { rs, _ ->
                UserActivitySummary(
                    submissionCount = rs.getInt("submission_count"),
                    lastSubmittedAt = rs.getTimestamp("last_submitted_at")?.toInstant(),
                )
            }
            .single()
}

data class UserActivitySummary(val submissionCount: Int, val lastSubmittedAt: Instant?)
