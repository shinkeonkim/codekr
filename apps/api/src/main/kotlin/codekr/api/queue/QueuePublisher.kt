package codekr.api.queue

import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.queue.message.ExecJobMessage
import codekr.api.queue.message.ExecResultMessage
import codekr.api.queue.message.JudgeEventMessage
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

    /**
     * 채점 작업을 **등급에 맞는 스트림**으로 보낸다 (#102).
     *
     * 등급을 인자로 받되, 그 값을 정하는 것은 호출부가 아니라 [JudgePriority.of] 다.
     * 요청에서 흘러들어올 경로가 없어야 한다.
     */
    fun publishJudgeJob(job: JudgeJobMessage, priority: JudgePriority) {
        add(priority.stream, objectMapper.writeValueAsString(job))
        log.debug("채점 작업 발행: submissionId={} priority={}", job.submissionId, priority)
    }

    /**
     * 채점 결과를 **채점기가 낸 것과 같은 길**로 흘려보낸다 (#650).
     *
     * 퀴즈는 실행기를 쓰지 않아 api 가 직접 채점하는데, 그 결과를 여기로 보내면
     * 뒤따르는 것이 전부 그대로 돈다 — 판정 기록·활동·점수·뱃지·문제 통계
     * (`JudgeResultRecorder`)와 실시간 중계(`/ws/submissions`)가 **새 경로 없이** 붙는다.
     *
     * 직접 부르지 않고 발행하는 이유가 하나 더 있다: api 파드가 여럿일 때 소켓이
     * 어느 파드에 붙어 있든 도달해야 한다. Pub/Sub 이 그것을 한다 (#9).
     *
     * **제출 행이 저장된 뒤에 불러야 한다.** 받는 쪽이 그 id 로 제출을 찾는다.
     */
    fun publishJudgeEvent(event: JudgeEventMessage) {
        redis.convertAndSend(QueueKeys.EVENT_CHANNEL, objectMapper.writeValueAsString(event))
        log.debug("채점 이벤트 발행: submissionId={} type={}", event.submissionId, event.type)
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
        extraFiles: Map<String, String> = emptyMap(),
    ): ExecResultMessage {
        val jobId = UUID.randomUUID().toString().replace("-", "")
        val replyStream = QueueKeys.REPLY_STREAM_PREFIX + jobId

        val job = ExecJobMessage(
            jobId, runtimeId, sourceCode, stdin, timeLimitMs, memoryLimitMb, replyStream, extraFiles,
        )
        /*
            **단발 실행은 일반 차선으로 간다** (#639).

            대회 차선으로 보내지 않는 이유: 이 경로(`/run`)는 대회를 모른다 — 문제만
            받는다. 대회 참가자가 누른 실행도 여기로 오지만, 그것까지 우대하려면
            대회 맥락이 이 엔드포인트까지 와야 하고 그것은 따로 볼 일이다.
        */
        add(QueueKeys.EXEC_STREAM_GENERAL, objectMapper.writeValueAsString(job))

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
