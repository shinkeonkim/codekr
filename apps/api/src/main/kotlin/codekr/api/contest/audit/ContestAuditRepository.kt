package codekr.api.contest.audit

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * 대회 제출의 감사 이력 (#148).
 *
 * **부정행위 의심이 생겼을 때 판단할 근거**다. 대회가 끝난 뒤에 기록을 만들 수는 없다.
 * 표절 탐지 자체는 비범위지만(#63), 나중에 검사하려면 데이터가 먼저 있어야 한다.
 */
@Repository
class ContestAuditRepository(private val jdbcClient: JdbcClient) {

    fun record(submissionId: Long, contestId: Long, userId: Long, ip: String?, userAgent: String?) {
        jdbcClient.sql(
            """
            INSERT INTO contest_submission_audits (submission_id, contest_id, user_id, ip, user_agent)
            VALUES (:submissionId, :contestId, :userId, :ip, :userAgent)
            ON CONFLICT (submission_id) DO NOTHING
            """,
        )
            .param("submissionId", submissionId)
            .param("contestId", contestId)
            .param("userId", userId)
            .param("ip", ip)
            .param("userAgent", userAgent?.take(USER_AGENT_LIMIT))
            .update()
    }

    /**
     * 한 대회에서 **같은 IP 로 낸 계정이 여럿인 경우**만 돌려준다.
     *
     * 전체 목록을 내리지 않는 이유: 운영자가 이유 없이 참가자의 IP 를 훑을 수 있게
     * 되면, 그것은 감사가 아니라 감시다. **의심스러운 것만** 보여준다.
     */
    fun sharedAddresses(contestId: Long): List<SharedAddress> =
        jdbcClient.sql(
            """
            SELECT a.ip, count(DISTINCT a.user_id)::int AS accounts,
                   string_agg(DISTINCT u.nickname, ', ' ORDER BY u.nickname) AS nicknames
            FROM contest_submission_audits a
            JOIN users u ON u.id = a.user_id
            WHERE a.contest_id = :contestId AND a.ip IS NOT NULL
            GROUP BY a.ip
            HAVING count(DISTINCT a.user_id) > 1
            ORDER BY count(DISTINCT a.user_id) DESC
            """,
        )
            .param("contestId", contestId)
            .query { rs, _ ->
                SharedAddress(rs.getString("ip"), rs.getInt("accounts"), rs.getString("nicknames"))
            }
            .list()

    /** 보관 기간이 지난 기록을 지운다 (ADR-0007). */
    fun deleteOlderThan(cutoff: Instant): Int =
        jdbcClient.sql("DELETE FROM contest_submission_audits WHERE created_at < :cutoff")
            .param("cutoff", cutoff.toString())
            .update()

    private companion object {
        /** User-Agent 는 길어질 수 있다. 판단에 필요한 만큼만 남긴다. */
        const val USER_AGENT_LIMIT = 500
    }
}

/** 같은 주소에서 낸 계정들. 참가자 본인 확인이 필요한 경우를 찾는 데 쓴다. */
data class SharedAddress(val ip: String, val accountCount: Int, val nicknames: String)
