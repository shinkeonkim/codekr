package codekr.api.ranking.entity

/**
 * 랭킹에 반영하는 문제 수 (#85).
 *
 * solved.ac 의 ac-rating 이 상위 일부만 반영하는 것과 같은 이유다 — 전부 더하면
 * 지표가 실력이 아니라 **누적 시간**을 나타내게 된다.
 */
const val SCORE_PROBLEM_LIMIT = 100

/**
 * 랭킹 지표 (#85).
 *
 * **랭킹은 하나일 이유가 없다.** 많이 푼 사람과 어려운 걸 푸는 사람은 서로 다르고,
 * 어느 쪽이 "더 잘하는"지는 지표를 골라야 답이 나온다.
 *
 * 지표를 하나만 두면 그 지표에 맞춘 행동만 유도한다 — 쉬운 문제만 대량으로 푸는 식.
 */
enum class RankingMetric(val label: String, val description: String) {
    /**
     * 난이도 가중 점수. **상위 [SCORE_PROBLEM_LIMIT] 문제만 합산한다.**
     *
     * 전부 더하면 쉬운 문제를 무한히 풀어 순위를 올릴 수 있다 — 브론즈 1000문제가
     * 루비 몇 문제를 이긴다. 상위 N개로 자르면 "가장 잘 푼 것"이 순위를 정한다.
     */
    SCORE("실력 점수", "가장 어려운 $SCORE_PROBLEM_LIMIT 문제의 난이도 점수 합"),

    /** 푼 문제 수. 꾸준함을 본다. */
    SOLVED_COUNT("푼 문제 수", "맞힌 서로 다른 문제의 수"),
}
