package codekr.api.realtime

import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.TextWebSocketHandler
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * 시험이 브라우저 대신 붙는 소켓 (#645).
 *
 * **받은 것을 큐에 쌓는다.** 콜백에서 바로 단언하면 그 실패가 시험 스레드가 아니라
 * 소켓 스레드에서 나서 **시험은 통과한 것처럼 보이고 로그에만 남는다.**
 *
 * [next] 는 기다리다 없으면 `null` 이다 — "안 왔다" 를 확인하는 시험이 있어서
 * 시간 초과를 예외로 만들지 않는다.
 */
class WebSocketProbe(port: Int, private val timeoutMs: Long = 5_000) : AutoCloseable {

    private val mapper = ObjectMapper()
    private val received = LinkedBlockingQueue<String>()
    private val session: WebSocketSession =
        StandardWebSocketClient()
            .execute(
                object : TextWebSocketHandler() {
                    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
                        received.put(message.payload)
                    }
                },
                "ws://localhost:$port/ws/submissions",
            )
            .get(timeoutMs, TimeUnit.MILLISECONDS)

    val isOpen: Boolean get() = session.isOpen

    fun send(payload: String) {
        session.sendMessage(TextMessage(payload))
    }

    fun subscribe(submissionId: Long, token: String) {
        send("""{"type":"SUBSCRIBE","submissionId":$submissionId,"token":"$token"}""")
    }

    /**
     * 다음 메시지. **"오지 않는다" 를 확인할 때는 이 대기 시간이 곧 확신의 크기**라
     * 짧게 두지 않는다.
     */
    fun next(): Map<*, *>? =
        received.poll(timeoutMs, TimeUnit.MILLISECONDS)?.let { mapper.readValue(it, Map::class.java) }

    override fun close() {
        runCatching { session.close(CloseStatus.NORMAL) }
    }
}
