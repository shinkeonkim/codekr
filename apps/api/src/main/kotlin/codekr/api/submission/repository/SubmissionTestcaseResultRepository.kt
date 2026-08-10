package codekr.api.submission.repository

import codekr.api.submission.entity.SubmissionTestcaseResult
import org.springframework.data.jpa.repository.JpaRepository

interface SubmissionTestcaseResultRepository : JpaRepository<SubmissionTestcaseResult, Long> {

    fun findBySubmissionIdOrderBySeqAsc(submissionId: Long): List<SubmissionTestcaseResult>

    fun findBySubmissionIdAndSeq(submissionId: Long, seq: Int): SubmissionTestcaseResult?
}
