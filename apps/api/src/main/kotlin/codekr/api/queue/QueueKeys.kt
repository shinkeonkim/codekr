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

    /** 대회 제출 전용 (#62). 전용 워커만 읽는다. */
    const val JUDGE_STREAM_CONTEST = "codekr:judge:contest"

    /**
     * 등급 스트림. **높은 등급부터.** Go 의 `JudgeStreamsByPriority()` 와 같은 목록이다.
     *
     * 대회 큐는 여기 없다 (#62) — 일반 워커가 읽으면 격리가 되지 않는다.
     */
    val JUDGE_PRIORITY_STREAMS = listOf(JUDGE_STREAM_HIGH, JUDGE_STREAM_NORMAL, JUDGE_STREAM_LOW)

    /**
     * 모니터링이 훑을 채점 스트림 전체.
     *
     * **대회 큐도 넣는다** (#62). 빼면 대회 중 적체가 어드민 화면에 보이지 않는데,
     * 적체를 볼 수 없으면 워커를 언제 늘려야 하는지 알 수 없다.
     */
    val JUDGE_STREAMS = JUDGE_PRIORITY_STREAMS + JUDGE_STREAM_CONTEST
    const val EXEC_STREAM = "codekr:exec"
    const val JUDGE_GROUP = "judge-workers"
    const val EXEC_GROUP = "exec-workers"
    const val EVENT_CHANNEL = "codekr:events"
    const val REPLY_STREAM_PREFIX = "codekr:exec:res:"
    const val PAYLOAD_FIELD = "payload"

    /** 스트림이 무한정 커지지 않도록 두는 근사 상한. */
    const val STREAM_MAX_LENGTH = 10_000L
}
