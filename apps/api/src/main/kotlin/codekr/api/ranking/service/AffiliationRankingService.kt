package codekr.api.ranking.service

import codekr.api.common.dto.PageResponse
import codekr.api.ranking.dto.AffiliationRankingEntry
import codekr.api.ranking.entity.RankingPeriod
import codekr.api.ranking.repository.AffiliationRankingRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 소속끼리 겨루는 랭킹 (#400).
 *
 * **지표를 고르게 하지 않는다.** 사람 랭킹은 실력 점수와 푼 문제 수를 고를 수 있지만,
 * 소속 점수는 "상위 N명의 합" 이라는 정의 자체가 점수 기반이다 — 푼 문제 수로 합하면
 * 쉬운 문제를 많이 푼 곳이 이기고, 그것이 사람 랭킹에서 이미 걷어낸 것이다(#85).
 */
@Service
@Transactional(readOnly = true)
class AffiliationRankingService(private val repository: AffiliationRankingRepository) {

    fun page(period: RankingPeriod, page: Int, size: Int): PageResponse<AffiliationRankingEntry> {
        val entries = repository.findPage(period, size, page * size)
        val total = repository.countRanked(period).toLong()
        return PageResponse(entries, page, size, total, ((total + size - 1) / size).toInt())
    }
}
