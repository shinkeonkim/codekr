package codekr.api.queue.message

data class ExecJobMessage(
    val jobId: String,
    val runtimeId: String,
    val sourceCode: String,
    val stdin: String,
    val timeLimitMs: Int,
    val memoryLimitMb: Int,
    val replyStream: String,
    /**
     * 문제가 함께 싣는 파일 (#525). SQL 문제의 `schema.sql` 이 여기로 온다.
     *
     * **정답(`answer.sql`)은 절대 실리지 않는다** — 실행 결과는 그대로 사용자에게
     * 돌아가므로, 실렸다면 하네스가 찍는 기대 결과가 화면에 그대로 보인다.
     */
    val extraFiles: Map<String, String> = emptyMap(),
)
