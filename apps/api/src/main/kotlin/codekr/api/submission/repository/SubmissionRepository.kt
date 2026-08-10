package codekr.api.submission.repository

import codekr.api.submission.entity.Submission
import codekr.api.submission.entity.SubmissionStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface SubmissionRepository : JpaRepository<Submission, Long> {

    fun findByIdAndDeletedAtIsNull(id: Long): Submission?

    fun findByUserIdAndDeletedAtIsNullOrderByIdDesc(userId: Long, pageable: Pageable): Page<Submission>

    fun findByUserIdAndProblemIdAndDeletedAtIsNullOrderByIdDesc(
        userId: Long,
        problemId: Long,
        pageable: Pageable,
    ): Page<Submission>

    /** 스위퍼가 오래 방치된 제출을 찾을 때 쓴다. */
    fun findByStatusInAndCreatedAtBefore(
        statuses: Collection<SubmissionStatus>,
        threshold: Instant,
    ): List<Submission>
}
