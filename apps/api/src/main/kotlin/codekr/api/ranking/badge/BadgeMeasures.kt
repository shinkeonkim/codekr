package codekr.api.ranking.badge

import codekr.api.activity.service.ActivityService
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component

/**
 * 조건의 재료 (#202, 설계는 #200 §4.1).
 *
 * **지표는 코드가 정한 목록에서만 고른다.** 자유 SQL 이 들어갈 자리가 없으므로
 * 운영자가 쓴 규칙이 임의 조회 창구가 되지 않는다 (#200 §8).
 *
 * 지표마다 질의는 **하나를 넘지 않는다.** 정답 확정은 채점 흐름 위에 있어서, 규칙이
 * 늘수록 질의가 느는 구조를 만들면 채점이 밀린다.
 */
@Component
class BadgeMeasures(
    private val jdbcClient: JdbcClient,
    private val activityService: ActivityService,
) {

    /**
     * 지표 하나. **이벤트 안에서 한 번만 계산한다** (#200 §6).
     *
     * 같은 이벤트에 `STREAK_7` 과 `STREAK_30` 이 함께 걸리면 스트릭을 두 번 읽지 않는다.
     */
    fun measure(name: String, event: BadgeEvent, cache: MutableMap<String, Any?>): Any? =
        cache.getOrPut(name) { compute(name, event) }

    /** 이번 이벤트가 속한 그룹. 파라미터화된 뱃지가 쓴다 (#200 §5). */
    fun group(name: String, event: BadgeEvent): String? = when (name) {
        "problem_category" -> event.problemId?.let {
            jdbcClient.sql("SELECT category FROM problems WHERE id = :id")
                .param("id", it)
                .query(String::class.java)
                .optional()
                .orElse(null)
        }
        else -> null
    }

    private fun compute(name: String, event: BadgeEvent): Any? = when (name) {
        /** 그 사람이 맞힌 문제 수. */
        "accepted_problem_count" -> jdbcClient
            .sql("SELECT count(*) FROM user_problem_scores WHERE user_id = :userId")
            .param("userId", event.userId)
            .query(Long::class.java)
            .single()

        /**
         * 이번 이벤트의 카테고리에서 맞힌 수.
         *
         * **모든 카테고리를 다시 세지 않는다** — 정답 하나가 확정될 때 바뀔 수 있는 것은
         * 그 문제의 카테고리뿐이다.
         */
        "accepted_in_category" -> event.problemId?.let { problemId ->
            jdbcClient.sql(
                """
                SELECT count(*)
                FROM user_problem_scores s
                JOIN problems p ON p.id = s.problem_id
                WHERE s.user_id = :userId
                  AND p.category = (SELECT category FROM problems WHERE id = :problemId)
                """,
            )
                .param("userId", event.userId)
                .param("problemId", problemId)
                .query(Long::class.java)
                .single()
        } ?: 0L

        /*
            대회 지표 (#463).

            **셋을 다 둔다.** "참가 등록만" · "한 번이라도 제출" · "한 문제라도 맞힘" 은
            서로 다른 문턱이고, 어느 것을 인정할지는 **규칙이 고를 일**이다 — 엔진이
            데이터 기반인 이유가 그것이다(#201·#202). 여기서 하나로 정해 버리면
            배포 없이 뱃지를 만든다는 약속이 반만 지켜진다.
        */

        /** 이번에 맞힌 것이 대회 제출인가. 이벤트 지표다 (#200 §4.1). */
        "is_contest_submission" -> event.contestId != null

        /** 등록한 대회 수. **나오지 않아도 센다** — 등록은 등록이다. */
        "contest_registered_count" -> jdbcClient
            .sql("SELECT count(*) FROM contest_registrations WHERE user_id = :userId")
            .param("userId", event.userId)
            .query(Long::class.java)
            .single()

        /**
         * 제출해 본 대회 수. **실제로 나온 증거다.**
         *
         * 맞히지 못해도 센다 — 처음 나온 사람에게 아무것도 못 받는 대회가 되지 않게
         * 하는 값이 여기 있다.
         */
        "contest_participated_count" -> jdbcClient
            .sql(
                """
                SELECT count(DISTINCT contest_id) FROM submissions
                WHERE user_id = :userId AND contest_id IS NOT NULL AND deleted_at IS NULL
                """,
            )
            .param("userId", event.userId)
            .query(Long::class.java)
            .single()

        /** 대회에서 맞힌 문제 수 (대회 × 문제로 센다). */
        "contest_accepted_count" -> jdbcClient
            .sql(
                """
                SELECT count(DISTINCT (contest_id, problem_id)) FROM submissions
                WHERE user_id = :userId AND contest_id IS NOT NULL
                  AND verdict = 'ACCEPTED' AND deleted_at IS NULL
                """,
            )
            .param("userId", event.userId)
            .query(Long::class.java)
            .single()

        /** 활동 집계에서 본다 (#105). **제출을 다시 훑지 않는다.** */
        "longest_streak_days" -> activityService.streaksOf(event.userId).longest.toLong()

        /**
         * 이벤트 지표 (#200 §4.1) — 내 기록만 봐서는 판정할 수 없다.
         *
         * 이미 점수 표에 기록된 뒤에 부르므로 자기 자신도 후보에 들어간다. 그래서
         * "나보다 이른 사람이 없다" 로 확인한다.
         */
        "is_first_solver" -> event.problemId?.let { problemId ->
            jdbcClient.sql(
                """
                SELECT NOT EXISTS (
                    SELECT 1 FROM user_problem_scores other
                    WHERE other.problem_id = :problemId
                      AND other.user_id <> :userId
                      AND other.solved_at <= (
                          SELECT mine.solved_at FROM user_problem_scores mine
                          WHERE mine.problem_id = :problemId AND mine.user_id = :userId
                      )
                )
                """,
            )
                .param("problemId", problemId)
                .param("userId", event.userId)
                .query(Boolean::class.java)
                .optional()
                .orElse(false)
        } ?: false

        // 모르는 지표는 조건을 만족하지 않은 것으로 본다. 규칙이 틀렸다고 채점을 멈추지 않는다.
        else -> null
    }
}
