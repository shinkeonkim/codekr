package codekr.api.observability

import codekr.api.config.properties.SubmissionProperties
import codekr.api.queue.QueueKeys
import codekr.api.queue.service.QueueMonitorService
import codekr.api.problem.entity.Difficulty
import codekr.api.problem.entity.Problem
import codekr.api.problem.entity.ProblemCategory
import codekr.api.problem.repository.ProblemRepository
import codekr.api.submission.entity.Submission
import codekr.api.submission.repository.SubmissionRepository
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import codekr.api.user.repository.UserRepository
import codekr.api.submission.service.StaleSubmissionSweeper
import codekr.api.support.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import kotlin.test.assertTrue

/**
 * api 지표가 **실제로 긁히는 본문에** 실린다 (#684).
 *
 * `MeterRegistry` 를 직접 들여다보는 시험은 등록됐는지만 본다. 정작 문제는 그것이
 * `/actuator/prometheus` 본문에 나오는가고, 이름이 어긋나면 대시보드 그래프가 조용히
 * 빈다 — 패널은 그대로 그려지고 선만 사라진다.
 */
class MetricsExposureIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var queueMetrics: QueueMetrics
    @Autowired private lateinit var monitor: QueueMonitorService
    @Autowired private lateinit var redisOfMetrics: StringRedisTemplate
    @Autowired private lateinit var sweeper: StaleSubmissionSweeper
    @Autowired private lateinit var submissionRepository: SubmissionRepository
    @Autowired private lateinit var properties: SubmissionProperties
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var problemRepository: ProblemRepository

    private fun scrape(): String =
        mockMvc.perform(get("/actuator/prometheus")).andReturn().response.contentAsString

    /**
     * 큐 게이지는 **정해진 간격으로 미리 읽어 둔다** — 긁힐 때 Redis 를 부르지 않는다.
     * 그래서 시험도 그 갱신을 한 번 돌린 뒤에 본다.
     */
    @Test
    fun `큐 게이지가 스트림별로 나온다`() {
        queueMetrics.refresh()

        val body = scrape()
        for (name in listOf("codekr_queue_length", "codekr_queue_lag", "codekr_queue_pending", "codekr_queue_consumers")) {
            assertTrue(body.contains(name), "$name 이 /actuator/prometheus 에 없습니다")
        }
        // **차선마다 나뉘어야 한다** (#639). 합쳐서 나오면 나눈 효과를 볼 수 없다.
        for (stream in listOf("codekr:judge:contest", "codekr:exec:contest", "codekr:exec:general")) {
            assertTrue(
                body.contains("""stream="$stream""""),
                "$stream 이 라벨로 안 나옵니다",
            )
        }
    }

    /**
     * **밀린 수와 남아 있는 수는 다른 값이다** (#702).
     *
     * `XLEN` 은 ack 해도 줄지 않는다 — Redis Streams 는 트리밍으로만 항목을 지운다.
     * 그래서 처리가 다 끝난 스트림도 길이가 남고, 그것을 "밀린 수" 로 그리면 영원히
     * 밀린 것처럼 보인다. 운영에서 `XLEN=10 · pending=0 · lag=0` 이었다.
     */
    @Test
    fun `처리가 끝나면 밀린 수는 0 이고 남아 있는 수는 0 이 아니다`() {
        val ops = redisOfMetrics.opsForStream<String, String>()
        redisOfMetrics.delete(QueueKeys.JUDGE_STREAM_HIGH)
        runCatching { ops.createGroup(QueueKeys.JUDGE_STREAM_HIGH, ReadOffset.from("0"), QueueKeys.JUDGE_GROUP) }
        ops.add(MapRecord.create(QueueKeys.JUDGE_STREAM_HIGH, mapOf("payload" to "{}")))
        // 읽고 ack 한다 — 처리가 끝난 상태다.
        val read = ops.read(
            Consumer.from(QueueKeys.JUDGE_GROUP, "확인용"),
            StreamOffset.create(QueueKeys.JUDGE_STREAM_HIGH, ReadOffset.lastConsumed()),
        )
        read?.forEach { ops.acknowledge(QueueKeys.JUDGE_GROUP, it) }

        val stream = monitor.status().streams.first { it.name == QueueKeys.JUDGE_STREAM_HIGH }

        assertTrue(stream.length > 0, "ack 해도 스트림 항목은 남는다. length=${stream.length}")
        assertTrue(stream.pending == 0L, "ack 했으므로 pending 은 0 이어야 한다: ${stream.pending}")
        assertTrue(stream.lag == 0L, "다 읽었으므로 밀린 것은 0 이어야 한다: ${stream.lag}")
    }

    /**
     * stale 종결 counter 는 **닫은 건수만큼** 는다.
     *
     * 값을 확인하는 이유: 등록만 되고 `increment` 를 안 하면 이름은 본문에 나오므로
     * 이름만 보는 시험은 통과한다.
     */
    @Test
    fun `stale 종결이 세어진다`() {
        val user = userRepository.save(User("stale@codekr.dev", "x", "낡은제출", setOf(UserRole.USER)))
        // 기본 상태가 PENDING 이고, 청소부는 PENDING·JUDGING 을 본다.
        val problem = problemRepository.save(
            Problem(
                slug = "stale-probe", title = "낡은 제출 확인용",
                category = ProblemCategory.ALGORITHM, difficultyLevel = Difficulty.BRONZE_5.level,
                description = "설명", published = true,
            ),
        )
        val stale = submissionRepository.save(Submission(user.id, problem.id, "python:3.12", "print(1)"))
        // 생성 시각은 엔티티가 정하므로, 임계 이전으로 직접 밀어 둔다.
        jdbcOfBase.sql("UPDATE submissions SET created_at = now() - make_interval(secs => ?) WHERE id = ?")
            .param(properties.staleTimeoutSeconds + 60)
            .param(stale.id)
            .update()

        val before = counterValue(scrape())
        sweeper.sweep()
        val after = counterValue(scrape())

        assertTrue(after == before + 1, "stale 종결이 세어지지 않았습니다: $before → $after")
    }

    private fun counterValue(body: String): Double =
        body.lineSequence()
            .firstOrNull { it.startsWith("codekr_submissions_stale_closed_total") && !it.startsWith("#") }
            ?.substringAfterLast(' ')
            ?.toDoubleOrNull()
            ?: 0.0
}
