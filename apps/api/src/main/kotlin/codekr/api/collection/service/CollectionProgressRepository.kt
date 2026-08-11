package codekr.api.collection.service

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

/**
 * 문제집 진행률 (#87).
 *
 * 랭킹 점수 표(#57)를 쓰지 않는 이유: 그 표는 **공개된 문제**만 담는다.
 * 문제집에는 비공개 문제가 들어갈 수 있고, 그때도 "내가 풀었다" 는 사실은 같다.
 */
@Repository
class CollectionProgressRepository(private val jdbcClient: JdbcClient) {

    fun solvedProblemIds(userId: Long, problemIds: Collection<Long>): Set<Long> {
        if (problemIds.isEmpty()) return emptySet()

        return jdbcClient.sql(
            """
            SELECT DISTINCT problem_id
            FROM submissions
            WHERE user_id = :userId
              AND problem_id IN (:problemIds)
              AND verdict = 'ACCEPTED'
              AND kind = 'USER'
              AND deleted_at IS NULL
            """,
        )
            .param("userId", userId)
            .param("problemIds", problemIds)
            .query(Long::class.java)
            .list()
            .filterNotNull()
            .toSet()
    }
}
