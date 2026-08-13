package codekr.api.problem.report

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface ProblemReportRepository : JpaRepository<ProblemReport, Long> {
    fun findByStatusOrderByIdDesc(status: ReportStatus, pageable: Pageable): Page<ProblemReport>
    fun findAllByOrderByIdDesc(pageable: Pageable): Page<ProblemReport>
    fun countByProblemIdAndStatus(problemId: Long, status: ReportStatus): Long
    fun existsByProblemIdAndReporterIdAndStatus(problemId: Long, reporterId: Long, status: ReportStatus): Boolean
}
