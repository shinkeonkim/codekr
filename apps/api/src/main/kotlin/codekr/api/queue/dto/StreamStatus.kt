package codekr.api.queue.dto

data class StreamStatus(
    val name: String,
    val group: String,
    val length: Long,
    val pending: Long,
    val consumers: Long,
    val lastDeliveredId: String?,
    /** 스트림이나 그룹이 아직 만들어지지 않았으면 false. 워커가 한 번도 뜨지 않은 상태다. */
    val ready: Boolean,
)
