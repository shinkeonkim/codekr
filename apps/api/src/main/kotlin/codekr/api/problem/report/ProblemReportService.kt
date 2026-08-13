package codekr.api.problem.report

import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.notification.entity.NotificationCategory
import codekr.api.notification.service.NotificationService
import codekr.api.problem.credit.CreditRole
import codekr.api.problem.credit.ProblemCredit
import codekr.api.problem.credit.ProblemCreditId
import codekr.api.problem.credit.ProblemCreditRepository
import codekr.api.problem.repository.ProblemRepository
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 문제 오류 신고 (#478).
 *
 * ## 받는 것만으로는 절반이다
 *
 * 신고를 받기만 하면 **한 번 하고 마는 일**이 된다. 받아들여진 신고는 **문제 페이지에
 * 이름을 남긴다** (#236 의 자리에 얹는다) — 그것이 계속할 이유가 된다. 사용자가 사이트에
 * 기여하는 통로라는 점에서 난이도 투표(#477)와 같은 결이다.
 *
 * ## 처리 결과를 반드시 알린다
 *
 * 말없이 닫으면 신고한 사람에게는 **읽지 않은 것과 구분되지 않는다.** 그리고 거절에는
 * 이유가 있어야 한다 — 이유 없는 거절은 다음 신고를 막는다.
 */
@Service
class ProblemReportService(
    private val reportRepository: ProblemReportRepository,
    private val problemRepository: ProblemRepository,
    private val creditRepository: ProblemCreditRepository,
    private val notificationService: NotificationService,
) {

    @Transactional
    fun report(problemId: Long, reporterId: Long, kind: ReportKind, body: String): ProblemReportResponse {
        if (body.isBlank()) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "무엇이 잘못됐는지 적어 주세요.")
        }
        /*
          **같은 사람이 같은 문제에 열린 신고를 둘 두지 않는다.** 열 명이 같은 것을
          말하는 것은 정보지만(그만큼 급하다는 뜻이다), 한 사람이 열 번 말하는 것은
          목록만 흐린다.
        */
        if (reportRepository.existsByProblemIdAndReporterIdAndStatus(problemId, reporterId, ReportStatus.OPEN)) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "이미 접수된 신고가 있습니다. 처리되면 알려 드립니다.")
        }
        val saved = reportRepository.save(ProblemReport(problemId, reporterId, kind, body.trim()))
        return ProblemReportResponse.from(saved, openCountOf(problemId))
    }

    @Transactional(readOnly = true)
    fun list(status: ReportStatus?, pageable: Pageable) =
        (status?.let { reportRepository.findByStatusOrderByIdDesc(it, pageable) }
            ?: reportRepository.findAllByOrderByIdDesc(pageable))
            .map { ProblemReportResponse.from(it, openCountOf(it.problemId)) }

    /**
     * 처리한다.
     *
     * 받아들이면 **신고자가 문제의 기여자로 남는다.** 거절하려면 이유를 적어야 한다.
     */
    @Transactional
    fun resolve(reportId: Long, adminId: Long, status: ReportStatus, resolution: String?): ProblemReportResponse {
        val report = reportRepository.findById(reportId).orElseThrow {
            ApiException(ErrorCode.VALIDATION_ERROR, "없는 신고입니다.")
        }
        if (report.status != ReportStatus.OPEN) {
            // 두 번 처리되면 알림이 두 번 가고, 어느 것이 결론인지 알 수 없다.
            throw ApiException(ErrorCode.VALIDATION_ERROR, "이미 처리된 신고입니다.")
        }
        if (status == ReportStatus.REJECTED && resolution.isNullOrBlank()) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "거절하려면 이유를 적어야 합니다.")
        }
        report.resolve(status, resolution?.trim(), adminId)

        val slug = problemRepository.findById(report.problemId).orElse(null)?.slug
        if (status == ReportStatus.ACCEPTED) {
            // **고친 사람이 남는다** (#236 의 자리). 이미 이름이 있으면 그대로 둔다.
            creditRepository.save(
                ProblemCredit(ProblemCreditId(report.problemId, report.reporterId, CreditRole.CONTRIBUTOR)),
            )
        }
        notificationService.notify(
            userId = report.reporterId,
            category = NotificationCategory.SYSTEM,
            title = if (status == ReportStatus.ACCEPTED) "신고한 문제가 고쳐졌습니다" else "신고를 처리했습니다",
            body = report.resolution,
            link = slug?.let { "/problems/$it" },
        )
        return ProblemReportResponse.from(report, openCountOf(report.problemId))
    }

    /** 이 문제에 열려 있는 신고 수. **열 명이 같은 것을 말하면 그만큼 급하다.** */
    private fun openCountOf(problemId: Long): Long =
        reportRepository.countByProblemIdAndStatus(problemId, ReportStatus.OPEN)
}
