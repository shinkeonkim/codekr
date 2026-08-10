package codekr.api.ranking.entity

/**
 * 랭킹 기간 (#58).
 *
 * 전체 누적만 두면 먼저 시작한 사람이 영원히 위에 있다. 월간이 있으면 이번 달에 시작한
 * 사람도 상위권에 닿을 수 있다.
 */
enum class RankingPeriod(val label: String) {
    ALL_TIME("전체"),
    MONTHLY("이번 달"),
}
