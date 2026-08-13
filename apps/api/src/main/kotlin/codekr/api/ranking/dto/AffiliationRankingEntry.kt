package codekr.api.ranking.dto

/**
 * 소속 랭킹 한 줄 (#400, #240 5단계).
 *
 * [memberCount] 를 함께 준다 — **점수가 상위 몇 명의 합인지 모르면 숫자가 무슨 뜻인지
 * 알 수 없다.** 인원이 순위를 가르지는 않지만, 읽는 사람에게는 맥락이다.
 */
data class AffiliationRankingEntry(
    val rank: Int,
    val affiliationId: Long,
    val name: String,
    val kindLabel: String,
    /** 상위 N명의 점수 합. N 은 최소 인원과 같다. */
    val score: Int,
    /** 지금 이 소속에 붙어 있는 사람 수 (탈퇴한 사람 제외). */
    val memberCount: Int,
)
