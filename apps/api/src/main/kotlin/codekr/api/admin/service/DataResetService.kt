package codekr.api.admin.service

import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.config.properties.DataResetProperties
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 문제와 그 위에 쌓인 것을 전부 비운다 (#285).
 *
 * ## 왜 소프트 삭제로는 안 되는가
 *
 * 문제·제출은 소프트 삭제다 (ADR-0007). 어드민 화면에서 아무리 지워도 **행은 남고
 * 시퀀스는 되돌아가지 않는다.** 첫 문제를 1번으로 시작하려면 `TRUNCATE ... RESTART
 * IDENTITY` 여야 한다 — 그리고 그것은 되돌릴 수 없다.
 *
 * ## 사람은 남기고 문제는 지운다
 *
 * 계정(`users`)·게시판(`posts`·`comments`)·분류(`tags`)는 남는다. 계정을 지우면 다시
 * 만들어야 하고, 게시판 글은 문제와 독립된 사람들의 글이다. 분류는 문제가 없어도
 * 그대로 쓸 수 있다.
 *
 * 문제를 가리키던 질문(#139)이 가리킬 곳을 잃는 것은 감수한다 — 이슈에서 정한 것이다.
 */
@Service
class DataResetService(
    private val jdbcClient: JdbcClient,
    private val properties: DataResetProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 비우는 표. **자식이 앞, 부모가 뒤다.**
     *
     * `TRUNCATE ... CASCADE` 를 쓰지 않는다. 그것은 `ON DELETE` 규칙을 **무시하고**
     * 참조하는 표를 통째로 비운다 — `posts.problem_id` 가 `problems` 를 가리키므로,
     * 문제를 TRUNCATE 하면 **게시판 글이 함께 사라진다.** 남기기로 한 표다.
     *
     * `DELETE` 는 `ON DELETE SET NULL` 을 지킨다. 문제를 가리키던 질문(#139)은 살아남고
     * 가리키던 곳만 비워진다 — 이슈에서 감수하기로 한 그것이다.
     *
     * (이 순서와 목록이 맞는지는 시험이 지킨다. 외래키가 하나 늘면 조용히 바뀐다.)
     */
    private val tables = listOf(
        // 제출 위에 쌓인 것부터
        "submission_testcase_results", "submission_views",
        "user_problem_scores", "user_daily_activity", "user_badges",
        "rejudge_submission_results", "rejudge_batches",
        // 대회 (제출을 가리킨다)
        "contest_submissions", "contest_registrations", "contest_notices",
        "contest_questions", "contest_problems", "contests",
        // 문제집
        "problem_collection_items", "problem_collections",
        // 제출
        "submissions",
        // 문제와 딸린 것
        "problem_testcases", "problem_templates", "problem_runtime_limits",
        "problem_sql_specs", "problem_tags", "problem_solution_verifications",
        "problems",
        // 지워진 것을 가리키는 알림이 남으면 안 된다
        "notifications",
    )

    /**
     * @param actor 누른 사람. **기록에 남긴다** — 이 조작이 남지 않으면 나중에
     *   "데이터가 왜 없지" 를 아무도 못 푼다. 어드민 관리 기록(#225)이 생기면 그리로 옮긴다.
     */
    @Transactional
    fun reset(actor: String): DataResetReport {
        if (!properties.enabled) {
            // 꺼져 있으면 **없는 기능**이다. 403 이 아니라 404 인 이유: 켜져 있는지
            // 여부까지 감춘다.
            throw ApiException(ErrorCode.FEATURE_DISABLED)
        }

        val existing = tables.filter(::exists)
        val before = existing.associateWith(::countOf).filterValues { it > 0 }

        log.warn("데이터 초기화를 시작합니다 (#285) — 요청자={}, 비울 표={}", actor, before)

        // 자식부터 지운다. 남은 참조는 각 외래키의 ON DELETE 규칙이 처리한다.
        existing.forEach { jdbcClient.sql("DELETE FROM $it").update() }
        // DELETE 는 시퀀스를 되돌리지 않는다. **첫 문제가 1번이어야 한다** (#204).
        existing.forEach(::restartSequence)

        log.warn("데이터 초기화를 마쳤습니다 — 요청자={}, 지운 행={}", actor, before.values.sum())
        return DataResetReport(clearedTables = before.keys.sorted(), clearedRows = before.values.sum())
    }

    /**
     * 있는 표만 비운다.
     *
     * 마이그레이션이 앞서거나 뒤서면 목록의 표가 아직 없을 수 있다. 그때 통째로
     * 실패하면 **초기화 자체를 못 하게 되는데**, 없는 표는 이미 비어 있는 것과 같다.
     */
    private fun exists(table: String): Boolean =
        jdbcClient.sql("SELECT to_regclass(:name) IS NOT NULL")
            .param("name", "public.$table")
            .query(Boolean::class.java)
            .single()

    /**
     * 그 표의 시퀀스를 1 부터 돌린다.
     *
     * **`id` 를 짐작하지 않는다.** 복합 키만 있는 표(`submission_views` 등)에는 `id` 가
     * 없어서 이름을 넣어 물으면 오류가 난다. 카탈로그에서 **기본값이 `nextval` 인 칸**을
     * 찾아 그 시퀀스만 되돌린다.
     */
    private fun restartSequence(table: String) {
        val sequences = jdbcClient.sql(
            """
            SELECT pg_get_serial_sequence(:qualified, column_name)
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = :table
              AND column_default LIKE 'nextval%'
            """,
        )
            .param("qualified", "public.$table")
            .param("table", table)
            .query(String::class.java)
            .list()

        sequences.filterNotNull().forEach {
            jdbcClient.sql("ALTER SEQUENCE $it RESTART WITH 1").update()
        }
    }

    private fun countOf(table: String): Long =
        jdbcClient.sql("SELECT count(*) FROM $table").query(Long::class.java).single()
}

/** 무엇이 얼마나 지워졌는지. 화면이 결과를 보여 준다. */
data class DataResetReport(val clearedTables: List<String>, val clearedRows: Long)
