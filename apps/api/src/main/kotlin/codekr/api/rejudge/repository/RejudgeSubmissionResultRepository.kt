package codekr.api.rejudge.repository

import codekr.api.rejudge.entity.RejudgeSubmissionResult
import codekr.api.rejudge.entity.UserRejudgeSummary
import codekr.api.submission.entity.Verdict
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

@Repository
class RejudgeSubmissionResultRepository(private val jdbcClient: JdbcClient) {

    /**
     * 전이 한 줄을 남긴다.
     *
     * **같은 제출이 다시 오면 덮어쓴다.** 채점 이벤트는 재전달될 수 있고, 두 줄이 되면
     * 요약의 건수가 어긋난다.
     */
    fun record(batchId: Long, result: RejudgeSubmissionResult) {
        jdbcClient.sql(
            """
            INSERT INTO rejudge_submission_results
                (batch_id, submission_id, user_id, previous_verdict, new_verdict, score_delta)
            VALUES (:batchId, :submissionId, :userId, :previous, :new, :scoreDelta)
            ON CONFLICT (batch_id, submission_id)
            DO UPDATE SET previous_verdict = EXCLUDED.previous_verdict,
                          new_verdict = EXCLUDED.new_verdict,
                          score_delta = EXCLUDED.score_delta
            """,
        )
            .param("batchId", batchId)
            .param("submissionId", result.submissionId)
            .param("userId", result.userId)
            .param("previous", result.previousVerdict?.name)
            .param("new", result.newVerdict?.name)
            .param("scoreDelta", result.scoreDelta)
            .update()
    }

    /** 배치의 결과를 사람별로 묶는다. 한 사람이 같은 문제에 여러 번 냈을 수 있다. */
    fun summarize(batchId: Long): List<UserRejudgeSummary> =
        jdbcClient.sql(
            """
            SELECT submission_id, user_id, previous_verdict, new_verdict, score_delta
            FROM rejudge_submission_results
            WHERE batch_id = :batchId
            ORDER BY user_id, submission_id
            """,
        )
            .param("batchId", batchId)
            .query { rs, _ ->
                RejudgeSubmissionResult(
                    submissionId = rs.getLong("submission_id"),
                    userId = rs.getLong("user_id"),
                    previousVerdict = rs.getString("previous_verdict")?.let(Verdict::valueOf),
                    newVerdict = rs.getString("new_verdict")?.let(Verdict::valueOf),
                    scoreDelta = rs.getInt("score_delta"),
                )
            }
            .list()
            .groupBy { it.userId }
            .map { (userId, results) -> UserRejudgeSummary(userId, results) }
}
