package codekr.api.observability

/**
 * api 가 내보내는 지표 이름 (#684).
 *
 * **대시보드가 이 이름에 매인다.** 바꾸면 그래프가 조용히 빈다 — 패널은 그대로 그려지고
 * 선만 사라지므로, 보는 사람은 "제출이 없었나 보다" 로 읽는다. Go 워커 쪽 이름을
 * `libs/gocontract/metrics.go` 에 모아 둔 것(#678)과 같은 이유로 여기 모은다.
 *
 * **Micrometer 이름은 점으로 쓰고 Prometheus 에서 밑줄이 된다.** counter 에는 `_total`
 * 이 붙는다 — 아래 주석의 오른쪽이 실제로 긁히는 이름이다.
 */
object MetricNames {

    /** `codekr_queue_length` — 스트림에 쌓인 메시지 수. */
    const val QUEUE_LENGTH = "codekr.queue.length"

    /** `codekr_queue_pending` — 읽어 갔지만 아직 ack 되지 않은 수. 워커가 붙들고 있는 양이다. */
    const val QUEUE_PENDING = "codekr.queue.pending"

    /**
     * `codekr_queue_consumers` — **최근 10분 안에 읽은** 소비자 수.
     *
     * **0 이면 아무도 안 읽고 있다.** 스트림 길이만 보면 "일이 없어서 0" 과 "읽는 이가
     * 없어서 안 줄어든다" 가 구분되지 않는다.
     *
     * **등록된 수가 아니다** (#699). `XINFO GROUPS` 의 `consumers` 는 한 번이라도
     * 등록된 이름을 전부 세는데, 소비자 이름이 파드 이름이라(#415) 배포마다 죽은
     * 이름이 쌓인다 — 운영에서 51개 중 살아 있는 것이 1개였다. 그 값으로는 위 문장을
     * 말할 수 없다. 판단은 `QueueMonitorService` 가 한다.
     */
    const val QUEUE_CONSUMERS = "codekr.queue.consumers"

    /**
     * `codekr_submissions_stale_closed_total` — 180초를 넘겨 `SYSTEM_ERROR` 로 닫은 제출 수.
     *
     * 조용히 늘면 큰일이다 — 사용자에게는 "채점 실패" 로만 보이고, 지금까지 이것은
     * 로그 한 줄이었다 (ADR-0004).
     */
    const val STALE_CLOSED = "codekr.submissions.stale.closed"

    /** 큐 지표가 공통으로 쓰는 태그. */
    const val TAG_STREAM = "stream"
    const val TAG_GROUP = "group"
}
