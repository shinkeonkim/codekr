package codekr.api.rejudge.dto

import codekr.api.rejudge.entity.RejudgeBatch
import java.time.Instant

/**
 * 재채점 진행 상황 (#107).
 *
 * 진행 중임을 화면이 표시할 수 있어야 한다 — 중간 상태의 통계·순위를 확정된 것처럼
 * 보여주면 사용자가 잘못된 정보로 판단한다.
 */
data class RejudgeResponse(
    val id: Long,
    val problemId: Long,
    val reason: String,
    val targetCount: Int,
    /** 결과가 돌아온 제출 수 (#187). 화면이 진행률을 보여주려면 이것이 필요하다. */
    val processedCount: Int,
    val changedCount: Int,
    val finished: Boolean,
    val createdAt: Instant,
) {
    companion object {
        fun from(batch: RejudgeBatch) = RejudgeResponse(
            id = batch.id,
            problemId = batch.problemId,
            reason = batch.reason,
            targetCount = batch.targetCount,
            processedCount = batch.processedCount,
            changedCount = batch.changedCount,
            finished = batch.isFinished,
            createdAt = batch.createdAt,
        )
    }
}
