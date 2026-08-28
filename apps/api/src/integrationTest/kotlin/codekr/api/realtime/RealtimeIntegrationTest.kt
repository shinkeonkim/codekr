package codekr.api.realtime

import codekr.api.auth.security.JwtTokenProvider
import codekr.api.problem.entity.Difficulty
import codekr.api.problem.entity.Problem
import codekr.api.problem.entity.ProblemCategory
import codekr.api.problem.repository.ProblemRepository
import codekr.api.queue.QueueKeys
import codekr.api.submission.entity.Submission
import codekr.api.submission.repository.SubmissionRepository
import codekr.api.support.IntegrationTestBase
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import codekr.api.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.data.redis.core.StringRedisTemplate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 실시간 채점 중계 (#645).
 *
 * **여기가 비어 있던 것이 위험했다.** 화면은 제출 뒤 폴링하지 않고 이 소켓을 붙들고
 * `JUDGING` → `TESTCASE` → `COMPLETED` 를 기다린다. 중계가 죽으면 채점은 멀쩡히 끝나
 * 있는데 사용자에게는 **영원히 "채점 중"** 이고, 그 상태를 잡는 시험이 하나도 없었다
 * (`rg "realtime|/ws/submissions|WebSocket" src/integrationTest src/test` → 0건).
 *
 * **가짜 세션으로 하지 않는다.** 손으로 만든 세션에 메시지를 넣으면 핸들러의 규칙은
 * 확인되지만 **그 핸들러가 `/ws/submissions` 에 걸려 있는지는 확인되지 않는다** —
 * #475 가 겪은 것이 정확히 그 자리다(코드도 차트도 각자 맞았고 둘 사이가 비어 있었다).
 * 그래서 서버를 띄우고 진짜로 붙는다.
 *
 * `IntegrationTestBase` 를 이어받되 **서버를 실제로 띄운다** — 컨테이너는 부모의 것을
 * 그대로 쓰므로 늘어나는 것은 스프링 컨텍스트 하나뿐이다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RealtimeIntegrationTest : IntegrationTestBase() {

    @LocalServerPort private var port: Int = 0

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var problemRepository: ProblemRepository
    @Autowired private lateinit var submissionRepository: SubmissionRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var redisTemplate: StringRedisTemplate

    private lateinit var ownerToken: String
    private lateinit var otherToken: String
    private lateinit var adminToken: String
    private var submissionId: Long = 0

    @BeforeEach
    fun setUpSubmission() {
        val owner = userRepository.save(User("owner@codekr.dev", "x", "제출자", setOf(UserRole.USER)))
        ownerToken = tokenProvider.issueAccessToken(owner)
        otherToken = tokenProvider.issueAccessToken(
            userRepository.save(User("other@codekr.dev", "x", "타인", setOf(UserRole.USER))),
        )
        adminToken = tokenProvider.issueAccessToken(
            userRepository.save(User("admin@codekr.dev", "x", "운영자", setOf(UserRole.ADMIN))),
        )
        val problem = problemRepository.save(
            Problem(
                slug = "two-sum", title = "두 수의 합",
                category = ProblemCategory.ALGORITHM, difficultyLevel = Difficulty.BRONZE_5.level,
                description = "설명", published = true,
            ),
        )
        submissionId = submissionRepository.save(
            Submission(owner.id, problem.id, "python:3.12", "print(1)"),
        ).id
    }

    @Test
    fun `제출자는 자기 제출을 구독한다`() {
        WebSocketProbe(port).use { probe ->
            probe.subscribe(submissionId, ownerToken)

            val reply = probe.next()
            assertEquals("SUBSCRIBED", reply?.get("type"))
            assertEquals(submissionId.toInt(), reply?.get("submissionId"))
        }
    }

    /**
     * **남의 제출을 엿볼 수 없다.**
     *
     * REST 쪽 권한은 #71 이 `EndpointAuthorizationIntegrationTest` 로 덮었는데
     * **소켓은 그 목록에 없다** — 여기서 막지 않으면 다른 곳에서 막을 자리가 없다.
     */
    @Test
    fun `남의 제출은 구독할 수 없다`() {
        WebSocketProbe(port).use { probe ->
            probe.subscribe(submissionId, otherToken)

            assertEquals("ERROR", probe.next()?.get("type"))
        }
    }

    /** 운영자는 본다 — 신고·재채점을 확인하려면 남의 채점을 따라가야 한다. */
    @Test
    fun `운영자는 남의 제출도 구독한다`() {
        WebSocketProbe(port).use { probe ->
            probe.subscribe(submissionId, adminToken)

            assertEquals("SUBSCRIBED", probe.next()?.get("type"))
        }
    }

    @Test
    fun `토큰이 없거나 엉뚱하면 구독할 수 없다`() {
        for (token in listOf("", "엉뚱한-토큰")) {
            WebSocketProbe(port).use { probe ->
                probe.subscribe(submissionId, token)

                assertEquals("ERROR", probe.next()?.get("type"), "토큰=$token")
            }
        }
    }

    /**
     * 없는 제출은 **없다고 답한다.**
     *
     * 이 갈래가 없으면 구독이 받아들여지고 아무 이벤트도 오지 않아,
     * 화면에서 "채점이 느리다" 와 구별되지 않는다.
     */
    @Test
    fun `없는 제출은 구독할 수 없다`() {
        WebSocketProbe(port).use { probe ->
            probe.subscribe(submissionId + 9_999, ownerToken)

            assertEquals("ERROR", probe.next()?.get("type"))
        }
    }

    @Test
    fun `SUBSCRIBE 가 아닌 메시지는 오류로 답한다`() {
        for (payload in listOf("""{"type":"PING"}""", "이건 JSON 이 아니다")) {
            WebSocketProbe(port).use { probe ->
                probe.send(payload)

                assertEquals("ERROR", probe.next()?.get("type"), "보낸 것=$payload")
            }
        }
    }

    /**
     * **이 시험이 이 파일의 이유다.**
     *
     * 채점기가 Redis 에 발행한 것이 구독 중인 브라우저까지 오는지를 끝에서 끝까지 본다.
     * 중간에 있는 것은 `JudgeEventListener`(받아서 저장하고 중계) 하나뿐인데,
     * 그 파일은 이 시험 전까지 **한 줄도 실행된 적이 없었다.**
     */
    @Test
    fun `채점기가 발행한 이벤트가 구독자에게 온다`() {
        WebSocketProbe(port).use { probe ->
            probe.subscribe(submissionId, ownerToken)
            assertEquals("SUBSCRIBED", probe.next()?.get("type"))

            publishJudgeEvent(submissionId, "COMPLETED", "ACCEPTED")

            val event = probe.next()
            assertEquals("COMPLETED", event?.get("type"))
            assertEquals("ACCEPTED", event?.get("verdict"))
        }
    }

    /** 구독하지 않은 제출의 이벤트는 오지 않는다 — 오면 남의 채점이 보이는 것이다. */
    @Test
    fun `구독하지 않은 제출의 이벤트는 오지 않는다`() {
        WebSocketProbe(port).use { probe ->
            probe.subscribe(submissionId, ownerToken)
            assertEquals("SUBSCRIBED", probe.next()?.get("type"))

            publishJudgeEvent(submissionId + 1, "COMPLETED", "ACCEPTED")

            assertNull(probe.next(), "다른 제출의 이벤트가 왔습니다")
        }
    }

    /**
     * 이벤트는 **저장도 된다** — 소켓을 놓친 브라우저가 다시 물어보면 최신이 보여야 한다.
     *
     * 중계만 확인하면 이 절반이 비어도 통과한다.
     */
    @Test
    fun `중계된 이벤트는 제출에도 기록된다`() {
        WebSocketProbe(port).use { probe ->
            probe.subscribe(submissionId, ownerToken)
            assertEquals("SUBSCRIBED", probe.next()?.get("type"))

            publishJudgeEvent(submissionId, "COMPLETED", "WRONG_ANSWER")
            assertNotNull(probe.next())
        }

        val status = awaitStatus(submissionId)
        assertEquals("COMPLETED", status, "판정이 기록되지 않았습니다")
    }

    /** 끊은 세션에 계속 보내려 하지 않는다 — 남으면 브로드캐스트마다 실패가 쌓인다. */
    @Test
    fun `끊긴 세션은 구독에서 빠진다`() {
        WebSocketProbe(port).use { probe ->
            probe.subscribe(submissionId, ownerToken)
            assertEquals("SUBSCRIBED", probe.next()?.get("type"))
        }

        // 끊긴 뒤에 발행해도 서버가 죽지 않고, 새 구독자는 정상으로 받는다.
        publishJudgeEvent(submissionId, "JUDGING", null)

        WebSocketProbe(port).use { probe ->
            probe.subscribe(submissionId, ownerToken)
            assertEquals("SUBSCRIBED", probe.next()?.get("type"))
            publishJudgeEvent(submissionId, "COMPLETED", "ACCEPTED")
            assertEquals("COMPLETED", probe.next()?.get("type"))
        }
    }

    private fun publishJudgeEvent(id: Long, type: String, verdict: String?) {
        val body = buildString {
            append("""{"type":"$type","submissionId":$id,"totalCount":1,"passedCount":1""")
            verdict?.let { append(""","verdict":"$it"""") }
            append("}")
        }
        redisTemplate.convertAndSend(QueueKeys.EVENT_CHANNEL, body)
    }

    /**
     * 발행은 Redis Pub/Sub 이라 **저장은 소켓 전송보다 늦을 수 있다.**
     * 고정 시간을 자면 느린 기계에서 깨지므로 조건이 될 때까지 짧게 되묻는다.
     */
    private fun awaitStatus(id: Long): String? {
        repeat(50) {
            val status = jdbcOfBase.sql("SELECT status FROM submissions WHERE id = :id")
                .param("id", id).query(String::class.java).optional().orElse(null)
            if (status != null && status != "PENDING") return status
            Thread.sleep(100)
        }
        return null
    }

    @Test
    fun `핸들러가 실제로 그 주소에 걸려 있다`() {
        // 붙는 것 자체가 확인이다 — 경로가 틀리면 여기서 예외가 난다.
        WebSocketProbe(port).use { probe -> assertTrue(probe.isOpen) }
    }
}
