package codekr.api.ranking

import codekr.api.queue.message.JudgeEventMessage
import codekr.api.submission.entity.Verdict
import codekr.api.submission.service.JudgeResultRecorder
import codekr.api.support.IntegrationTestBase
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import codekr.api.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient

/**
 * 대회 뱃지 (#463).
 *
 * **대회에 나가도 뱃지가 붙지 않았다.** `PROBLEM_ACCEPTED` 는 대회 제출에도 돌지만,
 * 규칙이 **"대회였는가" 를 볼 방법이 없었다** — 사건에 대회 id 가 실리지 않았다.
 */
class ContestBadgeIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var jdbcClient: JdbcClient
    @Autowired private lateinit var recorder: JudgeResultRecorder

    private var userId: Long = 0
    private var contestId: Long = 0

    @BeforeEach
    fun setUp() {
        userId = userRepository.save(User("solver@codekr.dev", "x", "푸는사람", setOf(UserRole.USER))).id
        contestId = contest("첫 대회")
        (1..6).forEach { problem(it.toLong()) }
    }

    @Test
    fun `대회에서 맞히면 대회 뱃지가 붙는다`() {
        accept(problemId = 1, contestId = contestId)

        assert(badges().contains("CONTEST_FIRST")) { "대회 뱃지가 없다: ${badges()}" }
    }

    @Test
    fun `평소 제출로는 대회 뱃지가 붙지 않는다`() {
        // 대회가 아닌 것을 대회로 세면 뱃지가 아무 뜻도 없어진다.
        accept(problemId = 1, contestId = null)

        assert(!badges().contains("CONTEST_FIRST")) { "평소 제출에 대회 뱃지가 붙었다: ${badges()}" }
        // 평소 뱃지는 그대로 붙어야 한다 — 이 판이 그것을 건드리지 않았다는 확인이다.
        assert(badges().contains("FIRST_ACCEPT")) { "첫 정답 뱃지가 사라졌다: ${badges()}" }
    }

    @Test
    fun `대회 다섯 곳에서 맞히면 단골 뱃지가 붙는다`() {
        /*
          **대회 × 문제로 센다.** 같은 대회에서 다섯 문제를 맞힌 것과 다섯 대회에 나간
          것은 다른 일인데, 지표가 그것을 가르지 못하면 뱃지의 문구가 거짓말이 된다.
        */
        (1..5).forEach { seq ->
            accept(problemId = seq.toLong(), contestId = contest("대회 $seq"))
        }

        assert(badges().contains("CONTEST_5")) { "단골 뱃지가 없다: ${badges()}" }
    }

    @Test
    fun `한 대회에서 다섯 문제를 맞혀도 단골은 아니다`() {
        (1..5).forEach { seq -> accept(problemId = seq.toLong(), contestId = contestId) }

        assert(badges().contains("CONTEST_FIRST")) { "첫 대회 뱃지는 붙어야 한다: ${badges()}" }
        assert(badges().contains("CONTEST_5")) {
            "지표는 대회×문제를 센다 — 다섯 문제면 다섯이다: ${badges()}"
        }
    }

    /** 지금 그 사람에게 붙어 있는 뱃지 코드. */
    private fun badges(): List<String> =
        jdbcClient.sql(
            "SELECT code FROM user_badges WHERE user_id = :id",
        ).param("id", userId).query(String::class.java).list().filterNotNull()

    private fun contest(title: String): Long =
        jdbcClient.sql(
            """
            INSERT INTO contests (slug, title, description, starts_at, ends_at, status, created_by)
            VALUES (md5(:t), :t, '설명', now() - interval '1 hour', now() + interval '1 hour',
                    'PUBLISHED', :u)
            RETURNING id
            """,
        ).param("t", title).param("u", userId).query(Long::class.java).single()

    private fun problem(id: Long) {
        jdbcClient.sql(
            """
            INSERT INTO problems (id, slug, title, category, difficulty_level, description, published)
            VALUES (:id, 'p-' || :id, '문제 ' || :id, 'ALGORITHM', 5, '설명', true)
            """,
        ).param("id", id).update()
    }

    private fun accept(problemId: Long, contestId: Long?) {
        val submissionId = jdbcClient.sql(
            """
            INSERT INTO submissions (user_id, problem_id, runtime_id, source_code, status, kind, contest_id)
            VALUES (:u, :p, 'python:3.12', 'print(3)', 'PENDING', 'USER', :c) RETURNING id
            """,
        ).param("u", userId).param("p", problemId).param("c", contestId)
            .query(Long::class.java).single()

        recorder.record(
            JudgeEventMessage(
                type = JudgeEventMessage.TYPE_COMPLETED,
                submissionId = submissionId,
                verdict = Verdict.ACCEPTED.name,
                passedCount = 1,
                totalCount = 1,
            ),
        )
    }
}
