package codekr.api.queue

import codekr.api.problem.entity.ExecutionLimits
import codekr.api.queue.message.ExecJobMessage
import codekr.api.queue.message.ExecResultMessage
import codekr.api.queue.message.JudgeEventMessage
import codekr.api.queue.message.JudgeJobMessage
import org.junit.jupiter.api.Test
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 큐 메시지는 Kotlin(api)과 Go(judge/executor)가 나눠 쓰는 계약이다.
 * 양쪽이 **같은 고정 JSON** 을 읽게 해서, 한쪽만 필드 이름이나 단위를 바꾸면 실패하게 만든다.
 *
 * Go 쪽 짝은 `libs/gocontract/contract_test.go` 이고 고정 JSON 도 그 모듈에 있다.
 */
class QueueContractTest {

    private val mapper = JsonMapper.builder()
        .addModule(KotlinModule.Builder().build())
        // 계약에 없는 필드가 있으면 실패한다 — Go 쪽이 필드를 늘리면 바로 드러난다.
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()

    private fun fixture(name: String): String =
        Files.readString(Path.of("../../libs/gocontract/testdata", name))

    @Test
    fun `채점 작업 고정 JSON 을 그대로 읽는다`() {
        val job = mapper.readValue(fixture("judge-job.json"), JudgeJobMessage::class.java)

        assertEquals(1024L, job.submissionId)
        assertEquals("python:3.12", job.runtimeId)
        assertEquals(2000, job.timeLimitMs)
        assertEquals(256, job.memoryLimitMb)
        assertEquals(1, job.testcases.size)
        assertEquals("3\n", job.testcases.first().expectedOutput)
    }

    @Test
    fun `실행 작업 고정 JSON 을 그대로 읽는다`() {
        val job = mapper.readValue(fixture("exec-job.json"), ExecJobMessage::class.java)

        assertEquals(QueueKeys.REPLY_STREAM_PREFIX + job.jobId, job.replyStream)
        assertEquals(2000, job.timeLimitMs)
        assertEquals(256, job.memoryLimitMb)
    }

    @Test
    fun `실행 결과 고정 JSON 을 그대로 읽는다`() {
        val result = mapper.readValue(fixture("exec-result.json"), ExecResultMessage::class.java)

        assertEquals("OK", result.status)
        assertEquals(24, result.runtimeMs)
        assertEquals(8192, result.memoryKb)
    }

    @Test
    fun `채점 이벤트 고정 JSON 을 그대로 읽는다`() {
        val event = mapper.readValue(fixture("judge-event.json"), JudgeEventMessage::class.java)

        assertEquals(JudgeEventMessage.TYPE_COMPLETED, event.type)
        assertEquals("WRONG_ANSWER", event.verdict)
        assertEquals(2, event.passedCount)
        assertEquals(3, event.totalCount)
    }

    @Test
    fun `직렬화한 메시지를 고정 JSON 과 같은 필드 이름으로 되읽는다`() {
        val original = mapper.readValue(fixture("judge-job.json"), JudgeJobMessage::class.java)

        val roundTrip = mapper.readValue(mapper.writeValueAsString(original), JudgeJobMessage::class.java)

        assertEquals(original, roundTrip)
    }

    @Test
    fun `실행 제약 허용 범위가 Go 쪽 정의와 같다`() {
        val goLimits = Files.readString(Path.of("../../libs/gocontract/limits.go"))

        // 두 언어가 같은 숫자를 쓰는지 확인한다. 값이 갈라지면 큐를 통과한 작업이 실행 직전에 거부된다.
        assertTrue(goLimits.contains("MinTimeLimitMs   = ${ExecutionLimits.MIN_TIME_LIMIT_MS}"))
        assertTrue(goLimits.contains("MaxTimeLimitMs   = ${ExecutionLimits.MAX_TIME_LIMIT_MS.toGoLiteral()}"))
        assertTrue(goLimits.contains("MinMemoryLimitMb = ${ExecutionLimits.MIN_MEMORY_LIMIT_MB}"))
        assertTrue(goLimits.contains("MaxMemoryLimitMb = ${ExecutionLimits.MAX_MEMORY_LIMIT_MB}"))
    }

    @Test
    fun `큐 키가 Go 쪽과 같은 값이다`() {
        // 키가 어긋나면 워커가 아무것도 못 읽는데, 오류 없이 조용히 멈춰서 발견이 늦다 (#102).
        val keys = mapper.readValue(fixture("queue-keys.json"), QueueKeysFixture::class.java)

        assertEquals(keys.judgeStreamsByPriority, QueueKeys.JUDGE_STREAMS)
        assertEquals(keys.execStream, QueueKeys.EXEC_STREAM)
        assertEquals(keys.judgeGroup, QueueKeys.JUDGE_GROUP)
        assertEquals(keys.execGroup, QueueKeys.EXEC_GROUP)
        assertEquals(keys.eventChannel, QueueKeys.EVENT_CHANNEL)
        assertEquals(keys.replyStreamPrefix, QueueKeys.REPLY_STREAM_PREFIX)
        assertEquals(keys.payloadField, QueueKeys.PAYLOAD_FIELD)
    }

    @Test
    fun `우선순위 등급과 스트림이 일대일로 대응한다`() {
        // 등급을 늘리면 스트림도 늘려야 한다. 빠뜨리면 그 등급의 작업이 어디로도 가지 않는다.
        assertEquals(QueueKeys.JUDGE_STREAMS.toSet(), JudgePriority.entries.map { it.stream }.toSet())
    }

    private data class QueueKeysFixture(
        val judgeStreamsByPriority: List<String>,
        val execStream: String,
        val judgeGroup: String,
        val execGroup: String,
        val eventChannel: String,
        val replyStreamPrefix: String,
        val payloadField: String,
    )
}

/** Go 소스의 숫자 리터럴은 가독성을 위해 밑줄을 쓴다 (30_000). */
private fun Int.toGoLiteral(): String = if (this >= 10_000) "%,d".format(this).replace(",", "_") else toString()
