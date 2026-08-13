package codekr.api.problem.report

import java.time.Instant

/** 신고 한 건 (#478). */
data class ProblemReportResponse(
    val id: Long,
    val problemId: Long,
    val reporterId: Long,
    val kind: ReportKind,
    val kindLabel: String,
    val body: String,
    val status: ReportStatus,
    val statusLabel: String,
    val resolution: String?,
    /**
     * 이 문제에 열려 있는 신고 수.
     *
     * **열 명이 같은 것을 말하면 그만큼 급하다는 뜻이다** — 목록에서 그것이 보여야
     * 어드민이 무엇부터 볼지 정할 수 있다.
     */
    val openCount: Long,
    val createdAt: Instant,
) {
    companion object {
        fun from(report: ProblemReport, openCount: Long) = ProblemReportResponse(
            id = report.id,
            problemId = report.problemId,
            reporterId = report.reporterId,
            kind = report.kind,
            kindLabel = report.kind.label,
            body = report.body,
            status = report.status,
            statusLabel = report.status.label,
            resolution = report.resolution,
            openCount = openCount,
            createdAt = report.createdAt,
        )
    }
}
