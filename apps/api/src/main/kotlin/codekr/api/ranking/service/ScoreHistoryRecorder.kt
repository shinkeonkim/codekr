package codekr.api.ranking.service

import codekr.api.ranking.entity.SkillTier
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZoneId

/**
 * 점수가 오른 날을 남긴다 (#476).
 *
 * **다시 계산하지 않고 기록한다.** `solved_at` 으로 "그날까지의 합" 을 계산할 수는
 * 있지만 그것은 **지금 난이도 기준**이고, 난이도는 바뀐다(#194) — 바뀔 때마다 과거
 * 그래프가 흔들리면 그래프를 믿을 수 없다.
 *
 * **하루 한 점이다.** 같은 날 여러 번 오르면 마지막 값이 남는다 — 그래프로 읽을 때
 * 하루보다 잘게 필요한 적이 없고, 잘게 남기면 표만 커진다.
 *
 * **제출 경로에 쓰기가 하나 는다** (#105 가 스트릭에서 한 경고). 그래서 여기서 하는
 * 일은 upsert 한 번뿐이고, 점수가 실제로 움직였을 때만 부른다.
 */
@Component
class ScoreHistoryRecorder(private val jdbcClient: JdbcClient) {

    @Transactional
    fun record(userId: Long, score: Int, on: LocalDate = LocalDate.now(KST)) {
        jdbcClient.sql(
            """
            INSERT INTO user_score_history (user_id, on_date, score, tier_level)
            VALUES (:user, :date, :score, :tier)
            ON CONFLICT (user_id, on_date)
            DO UPDATE SET score = EXCLUDED.score, tier_level = EXCLUDED.tier_level
            """,
        )
            .param("user", userId)
            .param("date", on)
            .param("score", score)
            // 티어가 없을 수도 있다 (#58 — 한 문제도 못 풀었으면 브론즈 5 가 아니라 없다).
            .param("tier", SkillTier.of(score)?.level ?: 0)
            .update()
    }

    private companion object {
        /** 날짜의 기준. 활동 그래프(#117)와 같은 시간대를 쓴다 — 두 그림이 어긋나면 안 된다. */
        val KST: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
