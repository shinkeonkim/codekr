package codekr.api.observability

import codekr.api.queue.service.QueueMonitorService
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong

/**
 * 큐 적체를 시계열로 남긴다 (#684).
 *
 * **어드민 큐 화면(#9, #13)은 *지금* 몇 건인지만 보여 준다.** "어제 대회 때 얼마나
 * 쌓였나" 를 물으면 답할 수 없고, 그것이 #639 로 큐를 나눈 효과를 재지 못하는 이유다.
 *
 * **[QueueMonitorService] 를 그대로 쓴다.** 같은 수를 두 곳이 세면 언젠가 갈리고,
 * 갈렸을 때 어느 쪽이 맞는지 알 방법이 없다 — 화면과 지표가 다른 수를 말하는 것이
 * 가장 나쁘다.
 */
@Component
class QueueMetrics(
    private val registry: MeterRegistry,
    private val monitor: QueueMonitorService,
) {

    /**
     * 스트림별 최신값. 게이지가 이것을 읽는다.
     *
     * **Micrometer 게이지는 대상을 약한 참조로 들고 있다.** 여기서 강하게 붙들지 않으면
     * GC 뒤에 값이 `NaN` 이 된다.
     */
    private val values = mutableMapOf<String, Holder>()

    private class Holder {
        val length = AtomicLong()
        /**
         * **-1 은 "Redis 가 계산하지 못했다" 다** (#702).
         *
         * 게이지는 값을 비울 수 없으므로 표시값을 하나 쓴다. 0 을 쓰면 "밀린 게 없다" 와
         * 구별되지 않고, 그 둘은 정반대다. 대시보드는 음수를 걸러 낸다.
         */
        val lag = AtomicLong(-1)
        val pending = AtomicLong()
        val consumers = AtomicLong()
    }

    /**
     * **긁힐 때 Redis 를 부르지 않는다.**
     *
     * Micrometer 게이지는 스크레이프마다 함수를 부르므로, 그대로 두면 `/actuator/prometheus`
     * 의 응답 시간이 Redis 에 매인다. 스트림이 일곱이고 하나마다 `XLEN`·`XINFO GROUPS`
     * 두 번이라 왕복이 열넷이다. 그리고 **Redis 가 느려진 그때가 이 지표를 가장 보고
     * 싶은 때인데**, 그때 스크레이프가 타임아웃 나면 아무것도 안 남는다.
     *
     * 그래서 정해진 간격으로 미리 읽어 둔다. 값은 최대 15초 낡을 수 있고, 스크레이프
     * 간격이 30초(`monitoring.interval`)라 문제가 되지 않는다.
     */
    @Scheduled(fixedDelayString = "PT15S")
    fun refresh() {
        for (stream in monitor.status().streams) {
            val holder = values.getOrPut(stream.name) { register(stream.name, stream.group) }
            holder.length.set(stream.length)
            holder.lag.set(stream.lag ?: -1)
            holder.pending.set(stream.pending)
            holder.consumers.set(stream.consumers)
        }
    }

    private fun register(stream: String, group: String): Holder {
        val holder = Holder()
        val tags = Tags.of(MetricNames.TAG_STREAM, stream, MetricNames.TAG_GROUP, group)
        registry.gauge(MetricNames.QUEUE_LENGTH, tags, holder.length)
        registry.gauge(MetricNames.QUEUE_LAG, tags, holder.lag)
        registry.gauge(MetricNames.QUEUE_PENDING, tags, holder.pending)
        registry.gauge(MetricNames.QUEUE_CONSUMERS, tags, holder.consumers)
        return holder
    }
}
