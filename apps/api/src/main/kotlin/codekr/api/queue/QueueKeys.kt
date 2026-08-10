package codekr.api.queue

/**
 * Redis 키와 큐 상수. Go 쪽 `libs/gocontract` 와 같은 값을 유지해야 한다
 * (docs/02_도메인_모델.md 5장).
 */
object QueueKeys {
    /**
     * 채점 큐는 우선순위 등급마다 스트림을 나눈다 (#102).
     * Go 쪽 libs/gocontract/contract.go 와 같은 값을 유지해야 한다.
     */
    const val JUDGE_STREAM_HIGH = "codekr:judge:high"
    const val JUDGE_STREAM_NORMAL = "codekr:judge:normal"
    const val JUDGE_STREAM_LOW = "codekr:judge:low"

    /** 모니터링이 훑을 채점 스트림 전체. 높은 등급부터. */
    val JUDGE_STREAMS = listOf(JUDGE_STREAM_HIGH, JUDGE_STREAM_NORMAL, JUDGE_STREAM_LOW)
    const val EXEC_STREAM = "codekr:exec"
    const val JUDGE_GROUP = "judge-workers"
    const val EXEC_GROUP = "exec-workers"
    const val EVENT_CHANNEL = "codekr:events"
    const val REPLY_STREAM_PREFIX = "codekr:exec:res:"
    const val PAYLOAD_FIELD = "payload"

    /** 스트림이 무한정 커지지 않도록 두는 근사 상한. */
    const val STREAM_MAX_LENGTH = 10_000L
}
