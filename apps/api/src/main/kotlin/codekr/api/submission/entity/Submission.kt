package codekr.api.submission.entity

import codekr.api.common.entity.SoftDeletableEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "submissions")
class Submission(

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "problem_id", nullable = false)
    val problemId: Long,

    @Column(name = "runtime_id", nullable = false, length = 40)
    val runtimeId: String,

    @Column(name = "source_code", nullable = false, columnDefinition = "text")
    val sourceCode: String,

    @Column(name = "total_count", nullable = false)
    var totalCount: Int = 0,

) : SoftDeletableEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: SubmissionStatus = SubmissionStatus.PENDING

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    var verdict: Verdict? = null

    @Column(name = "passed_count", nullable = false)
    var passedCount: Int = 0

    @Column(name = "max_runtime_ms", nullable = false)
    var maxRuntimeMs: Int = 0

    @Column(name = "max_memory_kb", nullable = false)
    var maxMemoryKb: Int = 0

    @Column(name = "compile_error", columnDefinition = "text")
    var compileError: String? = null

    /** 채점기가 작업을 집어 든 시점. 이미 종결된 제출은 되돌리지 않는다. */
    fun markJudging(totalCount: Int) {
        if (status == SubmissionStatus.PENDING) {
            status = SubmissionStatus.JUDGING
            this.totalCount = totalCount
        }
    }

    fun complete(result: JudgeOutcome) {
        status = SubmissionStatus.COMPLETED
        verdict = result.verdict
        passedCount = result.passedCount
        totalCount = result.totalCount
        maxRuntimeMs = result.maxRuntimeMs
        maxMemoryKb = result.maxMemoryKb
        compileError = result.compileError?.takeIf { it.isNotBlank() }
    }

    /** 채점이 끝나지 않은 채 방치된 제출을 종결한다. */
    fun fail(reason: Verdict = Verdict.SYSTEM_ERROR) {
        status = SubmissionStatus.FAILED
        verdict = reason
    }

    val isFinished: Boolean
        get() = status == SubmissionStatus.COMPLETED || status == SubmissionStatus.FAILED
}
