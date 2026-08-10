package codekr.api.queue

/**
 * Redis 키와 큐 상수. Go 쪽 `libs/gocontract` 와 같은 값을 유지해야 한다
 * (docs/02_도메인_모델.md 5장).
 */
object QueueKeys {
    const val JUDGE_STREAM = "codekr:judge"
    const val EXEC_STREAM = "codekr:exec"
    const val JUDGE_GROUP = "judge-workers"
    const val EXEC_GROUP = "exec-workers"
    const val EVENT_CHANNEL = "codekr:events"
    const val REPLY_STREAM_PREFIX = "codekr:exec:res:"
    const val PAYLOAD_FIELD = "payload"

    /** 스트림이 무한정 커지지 않도록 두는 근사 상한. */
    const val STREAM_MAX_LENGTH = 10_000L
}
