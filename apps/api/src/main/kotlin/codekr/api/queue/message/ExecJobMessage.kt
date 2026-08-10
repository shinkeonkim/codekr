package codekr.api.queue.message

data class ExecJobMessage(
    val jobId: String,
    val runtimeId: String,
    val sourceCode: String,
    val stdin: String,
    val timeLimitMs: Int,
    val memoryLimitMb: Int,
    val replyStream: String,
)
