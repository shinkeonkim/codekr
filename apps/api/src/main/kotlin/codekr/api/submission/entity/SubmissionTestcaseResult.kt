package codekr.api.submission.entity

import codekr.api.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 테스트케이스 단위 채점 결과.
 * `(submission_id, seq)` 유니크 제약이 이벤트 재전달에도 중복을 막는다.
 */
@Entity
@Table(name = "submission_testcase_results")
class SubmissionTestcaseResult(

    @Column(name = "submission_id", nullable = false)
    val submissionId: Long,

    @Column(nullable = false)
    val seq: Int,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var verdict: Verdict,

    @Column(name = "runtime_ms", nullable = false)
    var runtimeMs: Int = 0,

    @Column(name = "memory_kb", nullable = false)
    var memoryKb: Int = 0,

    @Column(name = "stderr_excerpt", columnDefinition = "text")
    var stderrExcerpt: String? = null,

) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    fun update(verdict: Verdict, runtimeMs: Int, memoryKb: Int, stderrExcerpt: String?) {
        this.verdict = verdict
        this.runtimeMs = runtimeMs
        this.memoryKb = memoryKb
        this.stderrExcerpt = stderrExcerpt
    }
}
