package codekr.api.observability

import codekr.api.config.properties.SubmissionProperties
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
        for (name in listOf("codekr_queue_length", "codekr_queue_pending", "codekr_queue_consumers")) {
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
