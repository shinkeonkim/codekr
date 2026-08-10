package codekr.api.config

import codekr.api.config.properties.CorsProperties
import codekr.api.queue.QueueKeys
import codekr.api.realtime.JudgeEventListener
import codekr.api.realtime.SubmissionWebSocketHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import java.util.concurrent.Executors

@Configuration
@EnableWebSocket
class RealtimeConfig(
    private val submissionWebSocketHandler: SubmissionWebSocketHandler,
    private val judgeEventListener: JudgeEventListener,
    private val corsProperties: CorsProperties,
) : WebSocketConfigurer {

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(submissionWebSocketHandler, "/ws/submissions")
            .setAllowedOrigins(*corsProperties.allowedOrigins.toTypedArray())
    }

    /**
     * 채점 이벤트 구독자. Pub/Sub 는 모든 구독자에게 브로드캐스트되므로
     * api 인스턴스가 여러 개여도 WebSocket 이 어디에 붙어 있든 이벤트가 도달한다.
     *
     * 처리 스레드를 하나로 묶는 이유: 여러 스레드로 나누면 같은 제출의 TESTCASE 이벤트가
     * COMPLETED 뒤에 도착해 화면에서 진행이 뒤집혀 보인다. 이벤트 하나의 처리 비용이
     * 작아(행 하나 upsert + 소켓 전송) 단일 스레드로도 충분하다.
     */
    @Bean
    fun judgeEventContainer(connectionFactory: RedisConnectionFactory): RedisMessageListenerContainer =
        RedisMessageListenerContainer().apply {
            setConnectionFactory(connectionFactory)
            setTaskExecutor(Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "judge-events") })
            addMessageListener(judgeEventListener, ChannelTopic(QueueKeys.EVENT_CHANNEL))
        }
}
