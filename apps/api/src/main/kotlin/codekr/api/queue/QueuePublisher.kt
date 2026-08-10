package codekr.api.queue

import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.queue.message.ExecJobMessage
import codekr.api.queue.message.ExecResultMessage
import codekr.api.queue.message.JudgeJobMessage
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.util.UUID

/**
 * 채점/실행 큐로 작업을 보낸다.
 *
 * 단발 실행(`/run`)은 응답이 필요하므로 작업별 응답 스트림을 만들어 결과를 기다린다.
 * 채점(`/submissions`)은 비동기이므로 발행만 하고 진행 상황은 이벤트로 받는다.
 */
@Component
class QueuePublisher(
    private val redis: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun publishJudgeJob(job: JudgeJobMessage) {
        add(QueueKeys.JUDGE_STREAM, objectMapper.writeValueAsString(job))
    }

    /**
     * 실행 작업을 보내고 결과를 기다린다.
     * 실행기가 응답하지 않으면 [ErrorCode.EXECUTION_FAILED] 로 끊는다.
     */
    fun runOnce(
        runtimeId: String,
        sourceCode: String,
        stdin: String,
        timeLimitMs: Int,
        memoryLimitMb: Int,
        waitTimeout: Duration,
    ): ExecResultMessage {
        val jobId = UUID.randomUUID().toString().replace("-", "")
        val replyStream = QueueKeys.REPLY_STREAM_PREFIX + jobId

        val job = ExecJobMessage(jobId, runtimeId, sourceCode, stdin, timeLimitMs, memoryLimitMb, replyStream)
        add(QueueKeys.EXEC_STREAM, objectMapper.writeValueAsString(job))

        return try {
            awaitReply(replyStream, waitTimeout)
        } finally {
            redis.delete(replyStream)
        }
    }

    private fun add(stream: String, payload: String) {
        // 스트림은 자동으로 줄어들지 않는다. 근사 트리밍으로 상한을 두어 메모리를 지킨다 (ADR-0002).
        redis.opsForStream<String, String>().add(
            MapRecord.create(stream, mapOf(QueueKeys.PAYLOAD_FIELD to payload)),
            XAddOptions.maxlen(QueueKeys.STREAM_MAX_LENGTH).approximateTrimming(true),
        )
    }

    private fun awaitReply(replyStream: String, waitTimeout: Duration): ExecResultMessage {
        val deadline = System.nanoTime() + waitTimeout.toNanos()
        while (System.nanoTime() < deadline) {
            val record = redis.opsForStream<String, String>()
                .read(StreamOffset.create(replyStream, ReadOffset.from("0")))
                .orEmpty()
                .firstOrNull()

            if (record != null) {
                val payload = record.value[QueueKeys.PAYLOAD_FIELD]
                    ?: throw ApiException(ErrorCode.EXECUTION_FAILED)
                return objectMapper.readValue(payload, ExecResultMessage::class.java)
            }
            Thread.sleep(POLL_INTERVAL_MS)
        }
        log.warn("실행 결과를 기다리다 시간이 초과되었습니다: {}", replyStream)
        throw ApiException(ErrorCode.EXECUTION_FAILED)
    }

    private companion object {
        /** 단발 요청 하나를 위해 구독자를 세우기보다, 가상 스레드에서 짧게 폴링하는 편이 싸다. */
        const val POLL_INTERVAL_MS = 50L
    }
}
