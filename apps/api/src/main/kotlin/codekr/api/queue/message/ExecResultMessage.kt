package codekr.api.queue.message

data class ExecResultMessage(
    val jobId: String = "",
    val status: String = "SYSTEM_ERROR",
    val exitCode: Int = 0,
    val stdout: String = "",
    val stderr: String = "",
    val runtimeMs: Int = 0,
    val memoryKb: Int = 0,
    val truncated: Boolean = false,
)
