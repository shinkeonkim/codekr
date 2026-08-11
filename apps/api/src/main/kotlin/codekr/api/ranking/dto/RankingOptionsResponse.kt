package codekr.api.ranking.dto

/**
 * 랭킹 화면이 고를 수 있는 축 (#58).
 *
 * **화면이 목록을 하드코딩하지 않게 서버가 알려준다** — 지표나 기간이 늘어날 때
 * 화면을 같이 고쳐야 하는 구조를 만들지 않는다.
 */
data class RankingOptionsResponse(
    val metrics: List<RankingMetricResponse>,
    val periods: List<RankingOption>,
)

data class RankingOption(val value: String, val label: String)
