package codekr.api.submission.dto

import codekr.api.queue.message.ExecResultMessage

data class RunResponse(
    val status: String,
    val stdout: String,
    val stderr: String,
    val runtimeMs: Int,
    val memoryKb: Int,
    val truncated: Boolean,
) {
    companion object {
        fun from(result: ExecResultMessage) = RunResponse(
            status = result.status,
            stdout = result.stdout,
            stderr = result.stderr,
            runtimeMs = result.runtimeMs,
            memoryKb = result.memoryKb,
            truncated = result.truncated,
        )
    }
}
