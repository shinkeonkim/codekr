package codekr.api.ranking.service

import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.user.repository.UserRepository
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * 점수가 어떻게 변해 왔는가 (#476).
 *
 * **남의 프로필에서도 보인다.** 활동 그래프(#117)가 이미 그렇고, 점수·티어·순위는
 * 랭킹(#57)이 이미 공개한다 — 여기서 새로 공개되는 것은 "언제 올랐는가" 뿐이다.
 */
@Service
class ScoreHistoryService(
    private val jdbcClient: JdbcClient,
    private val userRepository: UserRepository,
) {

    @Transactional(readOnly = true)
    fun of(handle: String, days: Int): List<ScorePoint> {
        val user = userRepository.findByHandle(handle)
            ?: userRepository.findByNickname(handle)
            ?: throw ApiException(ErrorCode.USER_NOT_FOUND)
        // 탈퇴한 계정의 기록은 열리지 않는다 (#140) — 프로필과 같은 규칙이다.
        if (user.isWithdrawn) throw ApiException(ErrorCode.USER_NOT_FOUND)

        return jdbcClient.sql(
            """
            SELECT on_date, score, tier_level
            FROM user_score_history
            WHERE user_id = :user AND on_date >= :from
            ORDER BY on_date
            """,
        )
            .param("user", user.id)
            .param("from", LocalDate.now().minusDays(days.coerceIn(1, 3650).toLong()))
            .query { rs, _ ->
                ScorePoint(
                    date = rs.getDate("on_date").toLocalDate(),
                    score = rs.getInt("score"),
                    tierLevel = rs.getInt("tier_level").takeIf { it > 0 },
                )
            }
            .list()
    }
}

/**
 * 그래프의 한 점 (#476).
 *
 * **점수를 선으로, 티어를 이정표로** 그린다 — 점수는 연속이라 변화가 보이고 티어는
 * 계단이라 읽기 쉽다. 티어가 없는 날(아직 한 문제도 못 푼)은 null 이다 (#58).
 */
data class ScorePoint(val date: LocalDate, val score: Int, val tierLevel: Int?)
