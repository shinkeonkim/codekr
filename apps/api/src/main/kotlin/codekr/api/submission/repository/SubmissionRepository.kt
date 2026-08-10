package codekr.api.submission.repository

import codekr.api.submission.entity.Submission
import codekr.api.submission.entity.SubmissionKind
import codekr.api.submission.entity.SubmissionStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface SubmissionRepository : JpaRepository<Submission, Long> {

    fun findByIdAndDeletedAtIsNull(id: Long): Submission?

    // 사용자에게 보이는 목록은 항상 kind 로 걸러 검증 제출이 섞이지 않게 한다.
    fun findByUserIdAndKindAndDeletedAtIsNullOrderByIdDesc(
        userId: Long,
        kind: SubmissionKind,
        pageable: Pageable,
    ): Page<Submission>

    fun findByUserIdAndProblemIdAndKindAndDeletedAtIsNullOrderByIdDesc(
        userId: Long,
        problemId: Long,
        kind: SubmissionKind,
        pageable: Pageable,
    ): Page<Submission>

    /** 스위퍼가 오래 방치된 제출을 찾을 때 쓴다. */
    fun findByStatusInAndCreatedAtBefore(
        statuses: Collection<SubmissionStatus>,
        threshold: Instant,
    ): List<Submission>
}
