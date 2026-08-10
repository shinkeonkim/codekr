package codekr.api.activity

import codekr.api.activity.repository.UserDailyActivityRepository
import codekr.api.activity.service.ActivityRecorder
import codekr.api.support.IntegrationTestBase
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import codekr.api.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals

/**
 * 집계 표가 어긋났을 때 되돌릴 수 있는지 (#105).
 *
 * **집계를 저장하기로 한 이상 이게 없으면 안 된다.** 되돌릴 방법이 없으면 애초에
 * 저장하면 안 되는 것이다 — 문제 통계(#84)에서는 그래서 저장하지 않는 쪽을 골랐다.
 */
class ActivityRecomputeIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var jdbcClient: JdbcClient
    @Autowired private lateinit var activityRepository: UserDailyActivityRepository
    @Autowired private lateinit var recorder: ActivityRecorder

    private val zone = ZoneId.of("Asia/Seoul")
    private var userId: Long = 0

    @BeforeEach
    fun setUp() {
        userId = userRepository.save(User("solver@codekr.dev", "x", "풀이왕", setOf(UserRole.USER))).id
        jdbcClient.sql(
            """
            INSERT INTO problems (id, slug, title, category, difficulty_level, description, published)
            VALUES (1, 'two-sum', '두 수의 합', 'ALGORITHM', 1, '설명', true)
            """,
        ).update()
    }

    @Test
    fun `망가진 집계를 재계산으로 되돌린다`() {
        val today = LocalDate.now(zone)
        repeat(3) { submissionOn(today) }
        activityRepository.recomputeAll(userId)
        assertEquals(3, countOn(today))

        // 값을 일부러 망가뜨린다 (증분 갱신이 어긋났을 때를 흉내낸다).
        jdbcClient.sql(
            "UPDATE user_daily_activity SET submission_count = 99 WHERE user_id = :userId",
        ).param("userId", userId).update()
        assertEquals(99, countOn(today))

        activityRepository.recomputeAll(userId)

        assertEquals(3, countOn(today), "재계산이 원자료와 정확히 맞춰야 합니다")
    }

    @Test
    fun `없는 날의 행이 남아 있으면 재계산이 지운다`() {
        val today = LocalDate.now(zone)
        jdbcClient.sql(
            """
            INSERT INTO user_daily_activity (user_id, activity_date, submission_count)
            VALUES (:userId, :date, 5)
            """,
        ).param("userId", userId).param("date", today.minusDays(10)).update()

        activityRepository.recomputeAll(userId)

        // 제출이 없던 날의 행은 사라져야 한다. 남으면 스트릭이 실제보다 길어진다.
        assertEquals(0, activityRepository.findActiveDates(userId).size)
        assertEquals(0, countOn(today))
    }

    @Test
    fun `같은 제출을 여러 번 반영해도 늘어나지 않는다`() {
        val today = LocalDate.now(zone)
        submissionOn(today)

        // 재채점(#107)은 같은 제출을 다시 COMPLETED 로 만든다.
        // 증분(+1) 방식이었다면 여기서 3이 된다.
        val at = today.atTime(12, 0).atZone(zone).toInstant()
        repeat(3) { recorder.recordCompletion(userId, at) }

        assertEquals(1, countOn(today))
    }

    @Test
    fun `제출이 모두 사라진 날은 행도 사라진다`() {
        val today = LocalDate.now(zone)
        submissionOn(today)
        val at = today.atTime(12, 0).atZone(zone).toInstant()
        recorder.recordCompletion(userId, at)
        assertEquals(1, countOn(today))

        jdbcClient.sql("UPDATE submissions SET deleted_at = now() WHERE user_id = :userId")
            .param("userId", userId).update()
        recorder.recordCompletion(userId, at)

        assertEquals(0, countOn(today))
    }

    @Test
    fun `맞힌 문제 수는 서로 다른 문제로 센다`() {
        val today = LocalDate.now(zone)
        // 같은 문제를 두 번 맞히고, 다른 문제를 한 번 맞힌다.
        acceptedOn(today, problemId = 1)
        acceptedOn(today, problemId = 1)
        acceptedOn(today, problemId = 2)
        submissionOn(today)

        activityRepository.recomputeAll(userId)

        val day = activityRepository.findDailyCounts(userId, today, today).first()
        assertEquals(4, day.count, "제출 수는 전부 센다")
        // 진한 칸이 '한 문제를 20번 틀린 날' 인지 '20문제를 푼 날' 인지 구분되어야 한다 (#133).
        assertEquals(2, day.solvedCount, "맞힌 문제는 서로 다른 문제로 센다")
    }

    @Test
    fun `집계와 재계산이 같은 값을 낸다`() {
        // **한쪽만 고치면 재계산이 값을 지운다** (#133).
        val today = LocalDate.now(zone)
        acceptedOn(today, problemId = 1)
        acceptedOn(today, problemId = 2)

        recorder.recordCompletion(userId, today.atTime(12, 0).atZone(zone).toInstant())
        val incremental = activityRepository.findDailyCounts(userId, today, today).first()

        activityRepository.recomputeAll(userId)
        val recomputed = activityRepository.findDailyCounts(userId, today, today).first()

        assertEquals(incremental.solvedCount, recomputed.solvedCount)
        assertEquals(2, recomputed.solvedCount)
    }

    private fun acceptedOn(date: LocalDate, problemId: Long) {
        jdbcClient.sql(
            """
            INSERT INTO problems (id, slug, title, category, difficulty_level, description, published)
            VALUES (:problemId, 'p-' || :problemId, '문제', 'ALGORITHM', 1, '설명', true)
            ON CONFLICT (id) DO NOTHING
            """,
        ).param("problemId", problemId).update()

        jdbcClient.sql(
            """
            INSERT INTO submissions
                (user_id, problem_id, runtime_id, source_code, status, verdict, kind, created_at, updated_at)
            VALUES (:userId, :problemId, 'python:3.12', 'print(3)', 'COMPLETED', 'ACCEPTED', 'USER',
                    :createdAt::timestamptz, now())
            """,
        )
            .param("userId", userId)
            .param("problemId", problemId)
            .param("createdAt", date.atTime(12, 0).atZone(zone).toInstant().toString())
            .update()
    }

    private fun submissionOn(date: LocalDate) {
        jdbcClient.sql(
            """
            INSERT INTO submissions
                (user_id, problem_id, runtime_id, source_code, status, kind, created_at, updated_at)
            VALUES (:userId, 1, 'python:3.12', 'print(3)', 'COMPLETED', 'USER', :createdAt::timestamptz, now())
            """,
        )
            .param("userId", userId)
            .param("createdAt", date.atTime(12, 0).atZone(zone).toInstant().toString())
            .update()
    }

    private fun countOn(date: LocalDate): Int =
        activityRepository.findDailyCounts(userId, date, date).firstOrNull()?.count ?: 0
}
