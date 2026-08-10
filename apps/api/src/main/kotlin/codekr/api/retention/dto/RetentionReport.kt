package codekr.api.retention.dto

import java.time.Instant

/** 정리 배치가 무엇을 지웠는지. 운영자가 확인할 수 있게 응답과 로그에 같은 모양으로 남긴다. */
data class RetentionReport(
    val executedAt: Instant,
    val deletedProblems: Int,
    val deletedTestcases: Int,
    val deletedTemplates: Int,
    val deletedNotifications: Int = 0,
    /** 상한(batchSize)에 걸려 남은 것이 있으면 true — 다음 실행에서 이어서 지운다. */
    val truncated: Boolean,
) {
    val total: Int get() = deletedProblems + deletedTestcases + deletedTemplates + deletedNotifications
}
