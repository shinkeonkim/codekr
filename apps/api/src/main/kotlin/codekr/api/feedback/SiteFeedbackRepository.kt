package codekr.api.feedback

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface SiteFeedbackRepository : JpaRepository<SiteFeedback, Long> {
    fun findByStatusOrderByIdDesc(status: FeedbackStatus, pageable: Pageable): Page<SiteFeedback>
    fun findAllByOrderByIdDesc(pageable: Pageable): Page<SiteFeedback>
    fun findByReporterIdOrderByIdDesc(reporterId: Long, pageable: Pageable): Page<SiteFeedback>
    fun countByReporterIdAndStatus(reporterId: Long, status: FeedbackStatus): Long
}
