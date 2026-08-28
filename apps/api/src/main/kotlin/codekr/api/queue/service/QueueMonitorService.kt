package codekr.api.queue.service

import codekr.api.queue.QueueKeys
import codekr.api.queue.dto.QueueStatusResponse
import codekr.api.queue.dto.StreamStatus
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service

/**
 * 채점/실행 큐의 적체 상황을 읽는다.
 * Redis Streams 는 XINFO/XPENDING 으로 상태를 그대로 노출하므로 별도 집계가 필요 없다 (ADR-0002).
 */
@Service
class QueueMonitorService(private val redis: StringRedisTemplate) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 채점 큐는 등급마다 스트림이 따로다 (#102). 등급별로 보여야 **무엇이 밀리고 있는지**
     * 알 수 있다 — 합쳐서 보여주면 낮은 등급이 밀리는 것과 전체가 밀리는 것을 구분할 수 없다.
     */
    fun status(): QueueStatusResponse = QueueStatusResponse(
        QueueKeys.JUDGE_STREAMS.map { inspect(it, QueueKeys.JUDGE_GROUP) } +
            // **차선마다 보여야 한다** (#639). 하나로 합쳐 보이면 대회가 밀리는 것과
            // 평소가 밀리는 것을 구별할 수 없고, 그러면 무엇을 늘려야 할지 알 수 없다.
            QueueKeys.EXEC_STREAMS_BY_PRIORITY.map { inspect(it, QueueKeys.EXEC_GROUP) },
    )

    private fun inspect(stream: String, group: String): StreamStatus {
        val operations = redis.opsForStream<String, String>()
        return runCatching {
            val length = operations.size(stream) ?: 0
            val groupInfo = operations.groups(stream).firstOrNull { it.groupName() == group }

            StreamStatus(
                name = stream,
                group = group,
                length = length,
                pending = groupInfo?.pendingCount() ?: 0,
                consumers = groupInfo?.consumerCount() ?: 0,
                lastDeliveredId = groupInfo?.lastDeliveredId(),
                ready = groupInfo != null,
            )
        }.getOrElse { error ->
            // 워커가 아직 한 번도 뜨지 않았으면 스트림 자체가 없다 — 오류가 아니라 상태다.
            log.debug("큐 상태 조회 실패: {}", stream, error)
            StreamStatus(stream, group, 0, 0, 0, null, ready = false)
        }
    }
}
