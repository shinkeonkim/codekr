package codekr.api.realtime

import codekr.api.auth.security.JwtTokenProvider
import codekr.api.auth.security.TokenType
import codekr.api.submission.repository.SubmissionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.ConcurrentHashMap

private data class SubscribeCommand(val type: String = "", val submissionId: Long = 0, val token: String = "")

/**
 * 채점 진행 상황을 브라우저로 흘려보내는 WebSocket 종단.
 *
 * 클라이언트는 접속 후 `{"type":"SUBSCRIBE","submissionId":N,"token":"..."}` 를 보낸다.
 * 구독 시점에 토큰과 소유권을 확인하므로, 남의 제출을 엿볼 수 없다.
 */
@Component
class SubmissionWebSocketHandler(
    private val tokenProvider: JwtTokenProvider,
    private val submissionRepository: SubmissionRepository,
    private val objectMapper: ObjectMapper,
) : TextWebSocketHandler() {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 제출 ID → 그 제출을 구독 중인 세션들. */
    private val subscribers = ConcurrentHashMap<Long, MutableSet<WebSocketSession>>()

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val command = runCatching {
            objectMapper.readValue(message.payload, SubscribeCommand::class.java)
        }.getOrNull()

        if (command == null || command.type != "SUBSCRIBE") {
            send(session, mapOf("type" to "ERROR", "message" to "SUBSCRIBE 메시지가 필요합니다."))
            return
        }
        if (!canRead(command)) {
            send(session, mapOf("type" to "ERROR", "message" to "구독 권한이 없습니다."))
            return
        }

        subscribers.computeIfAbsent(command.submissionId) { ConcurrentHashMap.newKeySet() }.add(session)
        send(session, mapOf("type" to "SUBSCRIBED", "submissionId" to command.submissionId))
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        subscribers.values.forEach { it.remove(session) }
        subscribers.entries.removeIf { it.value.isEmpty() }
    }

    /** 채점 이벤트를 해당 제출을 구독 중인 세션에 전달한다. */
    fun broadcast(submissionId: Long, payload: String) {
        val sessions = subscribers[submissionId] ?: return
        sessions.forEach { session ->
            runCatching { if (session.isOpen) session.sendMessage(TextMessage(payload)) }
                .onFailure { log.debug("WebSocket 전송 실패", it) }
        }
    }

    private fun canRead(command: SubscribeCommand): Boolean {
        val principal = tokenProvider.parse(command.token, TokenType.ACCESS) ?: return false
        val submission = submissionRepository.findByIdAndDeletedAtIsNull(command.submissionId) ?: return false
        return submission.userId == principal.userId || principal.isAdmin
    }

    private fun send(session: WebSocketSession, payload: Map<String, Any>) {
        runCatching { session.sendMessage(TextMessage(objectMapper.writeValueAsString(payload))) }
    }
}
