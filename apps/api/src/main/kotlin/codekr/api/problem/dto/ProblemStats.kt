package codekr.api.problem.dto

/**
 * 문제 풀이 통계 (#84).
 *
 * **제출 수가 아니라 사람 수로 센다.** 한 사람이 20번 제출한 것과 20명이 한 번씩 제출한
 * 것은 문제의 어려움에 대해 완전히 다른 이야기를 한다.
 *
 * 정답 검증용 제출(SOLUTION_VERIFICATION)은 세지 않는다 — 어드민이 만든 것이지
 * 누군가 문제를 푼 것이 아니다.
 */
data class ProblemStats(
    val submitterCount: Int,
    val solverCount: Int,
) {
    /**
     * 맞은 사람 / 제출한 사람. 아무도 제출하지 않았으면 null 이다.
     *
     * 0% 와 "아직 아무도 안 풀었음" 은 다른 사실이라 0 으로 뭉뚱그리지 않는다.
     */
    val acceptanceRate: Double?
        get() = if (submitterCount == 0) null else solverCount.toDouble() / submitterCount

    companion object {
        val EMPTY = ProblemStats(0, 0)
    }
}
