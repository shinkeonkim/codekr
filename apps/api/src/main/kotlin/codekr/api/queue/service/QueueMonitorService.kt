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

    fun status(): QueueStatusResponse = QueueStatusResponse(
        listOf(
            inspect(QueueKeys.JUDGE_STREAM, QueueKeys.JUDGE_GROUP),
            inspect(QueueKeys.EXEC_STREAM, QueueKeys.EXEC_GROUP),
        ),
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
