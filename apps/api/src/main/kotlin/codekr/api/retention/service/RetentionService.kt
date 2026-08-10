package codekr.api.retention.service

import codekr.api.config.properties.RetentionProperties
import codekr.api.retention.dto.RetentionReport
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 소프트 삭제된 행을 보관 기간이 지난 뒤 실제로 지운다 (#46, ADR-0007).
 *
 * **제출과 그 결과는 지우지 않는다.** 사용자의 기록이고, 문제가 지워져도 이력에 남아야 한다.
 * 여기서 지우는 것은 문제와 그 부산물(테스트케이스·초기 코드)뿐이다.
 */
@Service
class RetentionService(
    private val jdbcClient: JdbcClient,
    private val properties: RetentionProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 새벽 4시. 사용량이 가장 적고, 실패해도 다음 날 다시 시도하면 되는 시간대다. */
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    fun scheduledCleanup() {
        if (!properties.enabled) return
        val report = cleanup()
        if (report.deletedProblems + report.deletedTestcases + report.deletedTemplates > 0) {
            log.info("보관 기간이 지난 삭제 행 정리: {}", report)
        }
    }

    /**
     * 정리를 한 번 수행한다. 어드민이 직접 실행할 수도 있다.
     *
     * 자식(테스트케이스·초기 코드)을 먼저 지운다. 부모를 먼저 지우면 CASCADE 로 자식이
     * 함께 사라져 삭제 건수를 정확히 셀 수 없다.
     */
    @Transactional
    fun cleanup(now: Instant = Instant.now()): RetentionReport {
        val childThreshold = now.minusSeconds(properties.problemChildDays * SECONDS_PER_DAY)
        val problemThreshold = now.minusSeconds(properties.problemDays * SECONDS_PER_DAY)

        val testcases = deleteExpired("problem_testcases", childThreshold)
        val templates = deleteExpired("problem_templates", childThreshold)
        val problems = deleteExpiredProblems(problemThreshold)

        return RetentionReport(
            executedAt = now,
            deletedProblems = problems,
            deletedTestcases = testcases,
            deletedTemplates = templates,
            truncated = listOf(testcases, templates, problems).any { it >= properties.batchSize },
        )
    }

    private fun deleteExpired(table: String, threshold: Instant): Int =
        jdbcClient.sql(
            """
            DELETE FROM $table
            WHERE id IN (
                SELECT id FROM $table
                WHERE deleted_at IS NOT NULL AND deleted_at < :threshold
                ORDER BY deleted_at
                LIMIT :batchSize
            )
            """,
        )
            .param("threshold", java.sql.Timestamp.from(threshold))
            .param("batchSize", properties.batchSize)
            .update()

    /**
     * 제출 이력이 남아 있는 문제는 지우지 않는다.
     *
     * `submissions.problem_id` 가 `ON DELETE CASCADE` 라, 문제를 지우면 그 문제로 낸 제출까지
     * 사라진다. 이력 보존이 소프트 삭제를 도입한 이유이므로(ADR-0007) 그런 문제는 남긴다.
     */
    private fun deleteExpiredProblems(threshold: Instant): Int =
        jdbcClient.sql(
            """
            DELETE FROM problems
            WHERE id IN (
                SELECT p.id FROM problems p
                WHERE p.deleted_at IS NOT NULL
                  AND p.deleted_at < :threshold
                  AND NOT EXISTS (SELECT 1 FROM submissions s WHERE s.problem_id = p.id)
                ORDER BY p.deleted_at
                LIMIT :batchSize
            )
            """,
        )
            .param("threshold", java.sql.Timestamp.from(threshold))
            .param("batchSize", properties.batchSize)
            .update()

    private companion object {
        const val SECONDS_PER_DAY = 86_400L
    }
}
