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
    /**
     * 채점기가 언제까지 기다리는지 (#732).
     *
     * **전에는 이 값이 채점기 안에만 있었다.** 실행기는 자기 예산(이미지 받기 5분)으로
     * 일했고, 그래서 이미지가 없는 런타임은 **어느 조합에서도 성공할 수 없었다** —
     * 실행기가 5분을 다 쓰는 동안 채점기는 이미 1분 전에 포기했다.
     *
     * api 는 이 메시지를 만들지 않는다(채점기가 만든다). 여기 두는 이유는 **계약을
     * 양쪽 언어에서 같게 지키기 위해서**다 — 고정 JSON 시험이 그것을 본다.
     */
    val deadlineUnixMs: Long? = null,
)
