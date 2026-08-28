package codekr.api.queue.service

import codekr.api.queue.QueueKeys
import codekr.api.queue.dto.QueueStatusResponse
import codekr.api.queue.dto.StreamStatus
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * 채점/실행 큐의 적체 상황을 읽는다.
 * Redis Streams 는 XINFO/XPENDING 으로 상태를 그대로 노출하므로 별도 집계가 필요 없다 (ADR-0002).
 */
@Service
class QueueMonitorService(
    private val redis: StringRedisTemplate,
    /*
        **살아 있는 소비자를 가르는 기준** (#699).

        시험이 다른 값으로 확인할 수 있도록 인자로 받는다. 프로퍼티로 두지 않은 이유:
        운영에서 바꿀 값이 아니고, 프로퍼티를 하나 더하면 시험이 컨텍스트를 하나 더
        만들게 된다 (#645 가 그 대가를 겪었다).
    */
    private val activeWindow: Duration = ACTIVE_WINDOW,
) {

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

    /**
     * **최근에 읽은 소비자만 센다** (#699).
     *
     * `XINFO GROUPS` 의 `consumers` 는 **한 번이라도 등록된 이름을 전부** 센다.
     * 소비자 이름이 파드 이름이라(#415) 배포할 때마다 죽은 이름이 하나씩 쌓이고,
     * `XGROUP DELCONSUMER` 를 부르는 곳이 없어 영영 남는다.
     *
     * 운영에서 실제로 이랬다 — `codekr:judge:normal` 에 51개가 등록돼 있는데
     * **최근 5분 안에 읽은 것은 1개**, 가장 오래된 것은 15일 유휴였다. 그 값으로는
     * "0 이면 아무도 안 읽고 있다" 를 말할 수 없다. **워커가 전부 죽어도 51 이다.**
     *
     * ## `idle` 이 맞는 값이다
     *
     * `idle` 은 마지막 **상호작용**까지, `inactive` 는 마지막 **전달**까지다. 일감이
     * 없어도 워커는 계속 읽으므로 살아 있으면 `idle` 이 작다. 운영 실측:
     *
     * ```
     * 살아 있는 파드   idle=1.5s      inactive=2499.8s   ← 42분째 일감이 없다
     * 죽은 파드        idle=3892.2s
     * ```
     *
     * `inactive` 로 세면 **한가한 정상 워커가 죽은 것으로 세어진다.**
     */
    private fun activeConsumers(stream: String, group: String): Long =
        redis.opsForStream<String, String>()
            .consumers(stream, group)
            .count { it.idleTimeMs() < activeWindow.toMillis() }
            .toLong()

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
                consumers = if (groupInfo == null) 0 else activeConsumers(stream, group),
                lastDeliveredId = groupInfo?.lastDeliveredId(),
                ready = groupInfo != null,
            )
        }.getOrElse { error ->
            // 워커가 아직 한 번도 뜨지 않았으면 스트림 자체가 없다 — 오류가 아니라 상태다.
            log.debug("큐 상태 조회 실패: {}", stream, error)
            StreamStatus(stream, group, 0, 0, 0, null, ready = false)
        }
    }

    companion object {
        /**
         * 이 시간 안에 읽은 소비자만 살아 있는 것으로 본다.
         *
         * 워커의 폴링은 **2초 블록 + 1초 대기**라 한가해도 3초 안에 갱신된다(실측 1.5초).
         * 그런데 채점 하나가 최대 180초까지 갈 수 있고(ADR-0004) 그동안 그 파드의
         * 고루틴이 전부 바쁘면 읽지 않는다. **10분은 그 두 배 이상**이고, 실제로 죽은
         * 것들은 65분·38시간이었다 — 사이가 넓어 아슬아슬한 값이 아니다.
         *
         * 짧게 잡으면 바쁜 정상 워커가 죽은 것으로 세어져 **없는 경보가 울린다.**
         */
        private val ACTIVE_WINDOW: Duration = Duration.ofMinutes(10)
    }
}
