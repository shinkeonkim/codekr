package codekr.api.queue.dto

data class StreamStatus(
    val name: String,
    val group: String,
    /**
     * 스트림에 **남아 있는 항목 수**(`XLEN`). 밀린 수가 아니다 (#702).
     *
     * Redis Streams 는 ack 해도 항목을 지우지 않는다 — 트리밍(`MAXLEN ~`)으로만 준다.
     * 그래서 이 값은 처리가 끝난 것까지 센다. 밀린 것은 [lag] 이다.
     */
    val length: Long,
    /**
     * **그룹이 아직 안 읽은 수.** 이것이 밀린 것이다 (#702).
     *
     * null 이면 Redis 가 계산하지 못한 것이다 — 안 읽은 항목이 트리밍으로 잘렸을 때
     * 그렇다. **0 으로 바꾸지 않는다**: 0 은 "밀린 게 없다" 로 읽혀 정반대가 된다.
     */
    val lag: Long?,
    val pending: Long,
    val consumers: Long,
    val lastDeliveredId: String?,
    /** 스트림이나 그룹이 아직 만들어지지 않았으면 false. 워커가 한 번도 뜨지 않은 상태다. */
    val ready: Boolean,
)
