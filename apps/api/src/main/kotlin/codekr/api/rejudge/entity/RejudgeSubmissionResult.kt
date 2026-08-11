package codekr.api.rejudge.entity

import codekr.api.submission.entity.Verdict

/**
 * 재채점 한 건의 판정 전이 (#187).
 *
 * **바뀐 것만이 아니라 전부 남긴다.** "다시 채점했지만 결과는 그대로"라는 사실도 근거가
 * 되고, 나중에 "그때 무슨 일이 있었나"를 물었을 때 답할 수 있는 것은 이 표뿐이다.
 */
data class RejudgeSubmissionResult(
    val submissionId: Long,
    val userId: Long,
    val previousVerdict: Verdict?,
    val newVerdict: Verdict?,
    val scoreDelta: Int,
)

/** 한 사람의 재채점 결과 묶음. 배치가 끝났을 때 그 사람에게 한 번 알리기 위한 것이다. */
data class UserRejudgeSummary(
    val userId: Long,
    val results: List<RejudgeSubmissionResult>,
) {
    val changed: List<RejudgeSubmissionResult> get() = results.filter { it.previousVerdict != it.newVerdict }

    val scoreDelta: Int get() = results.sumOf { it.scoreDelta }
}
