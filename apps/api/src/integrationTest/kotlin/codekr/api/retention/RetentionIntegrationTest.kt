package codekr.api.retention

import codekr.api.retention.service.RetentionService
import codekr.api.support.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals

class RetentionIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var retentionService: RetentionService
    @Autowired private lateinit var jdbcClient: JdbcClient

    private val now = Instant.now()

    @Test
    fun `보관 기간이 지난 삭제 문제만 실제로 지운다`() {
        insertProblem(id = 1, slug = "old-deleted", deletedAt = now.minus(200, ChronoUnit.DAYS))
        insertProblem(id = 2, slug = "recently-deleted", deletedAt = now.minus(3, ChronoUnit.DAYS))
        insertProblem(id = 3, slug = "alive", deletedAt = null)

        val report = retentionService.cleanup(now)

        assertEquals(1, report.deletedProblems)
        assertEquals(setOf(2L, 3L), remainingProblemIds())
    }

    @Test
    fun `제출 이력이 있는 문제는 보관 기간이 지나도 남긴다`() {
        // 문제를 지우면 CASCADE 로 제출까지 사라진다 — 이력 보존이 소프트 삭제의 목적이다.
        insertProblem(id = 1, slug = "has-submissions", deletedAt = now.minus(200, ChronoUnit.DAYS))
        insertUser()
        insertSubmission(problemId = 1)

        val report = retentionService.cleanup(now)

        assertEquals(0, report.deletedProblems)
        assertEquals(setOf(1L), remainingProblemIds())
        assertEquals(1, countOf("submissions"))
    }

    @Test
    fun `문제 편집 부산물은 더 짧은 기간만 보관한다`() {
        insertProblem(id = 1, slug = "edited", deletedAt = null)
        // 기본 설정: 자식 30일, 문제 90일
        insertTestcase(problemId = 1, seq = 1, deletedAt = now.minus(60, ChronoUnit.DAYS))
        insertTestcase(problemId = 1, seq = 2, deletedAt = now.minus(5, ChronoUnit.DAYS))
        insertTestcase(problemId = 1, seq = 3, deletedAt = null)

        val report = retentionService.cleanup(now)

        assertEquals(1, report.deletedTestcases)
        assertEquals(2, countOf("problem_testcases"))
    }

    @Test
    fun `지울 것이 없으면 아무것도 지우지 않는다`() {
        insertProblem(id = 1, slug = "alive", deletedAt = null)

        val report = retentionService.cleanup(now)

        assertEquals(0, report.deletedProblems)
        assertEquals(0, report.deletedTestcases)
        assertEquals(0, report.deletedTemplates)
        assertEquals(false, report.truncated)
    }

    private fun remainingProblemIds(): Set<Long> =
        jdbcClient.sql("SELECT id FROM problems").query(Long::class.java).list().filterNotNull().toSet()

    private fun countOf(table: String): Int =
        jdbcClient.sql("SELECT count(*) FROM $table").query(Int::class.java).single()

    private fun insertProblem(id: Long, slug: String, deletedAt: Instant?) {
        jdbcClient.sql(
            """
            INSERT INTO problems (id, slug, title, category, difficulty_level, description, published, deleted_at)
            VALUES (:id, :slug, :slug, 'ALGORITHM', 1, '설명', true, :deletedAt)
            """,
        )
            .param("id", id)
            .param("slug", slug)
            .param("deletedAt", deletedAt?.let { java.sql.Timestamp.from(it) })
            .update()
    }

    private fun insertTestcase(problemId: Long, seq: Int, deletedAt: Instant?) {
        jdbcClient.sql(
            """
            INSERT INTO problem_testcases (problem_id, seq, input, expected_output, visibility, deleted_at)
            VALUES (:problemId, :seq, '1 2', '3', 'HIDDEN', :deletedAt)
            """,
        )
            .param("problemId", problemId)
            .param("seq", seq)
            .param("deletedAt", deletedAt?.let { java.sql.Timestamp.from(it) })
            .update()
    }

    private fun insertUser() {
        jdbcClient.sql(
            """
            INSERT INTO users (id, email, password_hash, nickname)
            VALUES (1, 'solver@codekr.dev', 'x', '풀이왕')
            """,
        ).update()
    }

    private fun insertSubmission(problemId: Long) {
        jdbcClient.sql(
            """
            INSERT INTO submissions (user_id, problem_id, runtime_id, source_code, status, kind)
            VALUES (1, :problemId, 'python:3.12', 'print(3)', 'COMPLETED', 'USER')
            """,
        ).param("problemId", problemId).update()
    }
}
