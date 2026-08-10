package codekr.api.realtime

import codekr.api.queue.message.JudgeEventMessage
import codekr.api.submission.service.JudgeResultRecorder
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.Message
import org.springframework.data.redis.connection.MessageListener
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * 채점기가 발행한 진행 이벤트를 받아 (1) 영속화하고 (2) WebSocket 으로 중계한다.
 *
 * 영속화를 먼저 하는 이유는, 브라우저가 이벤트를 놓치고 REST 로 다시 조회했을 때
 * 이미 최신 상태가 보이도록 하기 위함이다.
 */
@Component
class JudgeEventListener(
    private val recorder: JudgeResultRecorder,
    private val webSocketHandler: SubmissionWebSocketHandler,
    private val objectMapper: ObjectMapper,
) : MessageListener {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun onMessage(message: Message, pattern: ByteArray?) {
        val payload = String(message.body)
        val event = runCatching { objectMapper.readValue(payload, JudgeEventMessage::class.java) }
            .getOrElse {
                log.error("채점 이벤트 파싱 실패: {}", payload, it)
                return
            }

        runCatching { recorder.record(event) }
            .onFailure { log.error("채점 이벤트 영속화 실패: {}", event, it) }

        webSocketHandler.broadcast(event.submissionId, payload)
    }
}
