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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val kind: SubmissionKind = SubmissionKind.USER,

) : SoftDeletableEntity() {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var visibility: SubmissionVisibility = SubmissionVisibility.PRIVATE

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

    /** 재채점 중이면 그 배치. 끝나면 비운다 (#107). */
    @Column(name = "rejudge_batch_id")
    var rejudgeBatchId: Long? = null

    /**
     * 재채점 직전의 판정.
     *
     * 결과가 도착했을 때 **바뀌었는가**를 알아야 알림을 보낼지 정할 수 있다.
     * 안 바뀐 사람에게 보내면 소음이다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_verdict", length = 30)
    var previousVerdict: Verdict? = null

    /**
     * 재채점을 시작한다. 판정을 지우지 않고 **이전 값을 따로 보관**한다 —
     * 재채점 중에도 목록에는 지금까지의 판정이 보여야 한다.
     */
    fun startRejudge(batchId: Long) {
        rejudgeBatchId = batchId
        previousVerdict = verdict
        status = SubmissionStatus.PENDING
        passedCount = 0
    }

    /** 재채점 결과를 반영하고 **판정이 바뀌었는지** 돌려준다. */
    fun finishRejudge(): Boolean {
        val changed = previousVerdict != verdict
        rejudgeBatchId = null
        previousVerdict = null
        return changed
    }

    val isRejudging: Boolean get() = rejudgeBatchId != null

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

    /**
     * 이 제출의 소스 코드를 [viewerId] 가 볼 수 있는가.
     *
     * 접근 판단을 엔티티에 두는 이유는, 목록·상세·검색 등 여러 경로가 **같은 규칙**을 써야
     * 하기 때문이다. 규칙이 흩어지면 한 경로에서만 새는 사고가 난다.
     */
    fun isSourceVisibleTo(viewerId: Long?, isAdmin: Boolean): Boolean {
        if (isAdmin) return true
        if (viewerId != null && viewerId == userId) return true
        // 검증 제출의 소스는 정답 코드다 — 공개 범위와 무관하게 어드민만 본다.
        if (kind != SubmissionKind.USER) return false

        return when (visibility) {
            SubmissionVisibility.PUBLIC -> true
            SubmissionVisibility.PRIVATE -> false
            SubmissionVisibility.ACCEPTED_ONLY -> status == SubmissionStatus.COMPLETED &&
                verdict == Verdict.ACCEPTED
        }
    }

    fun changeVisibility(next: SubmissionVisibility) {
        visibility = next
    }
}
