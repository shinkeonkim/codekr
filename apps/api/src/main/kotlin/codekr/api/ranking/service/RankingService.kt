package codekr.api.ranking.service

import codekr.api.common.dto.PageResponse
import codekr.api.ranking.dto.RankingEntry
import codekr.api.ranking.entity.RankingMetric
import codekr.api.ranking.entity.RankingPeriod
import codekr.api.ranking.repository.RankingRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class RankingService(private val rankingRepository: RankingRepository) {

    fun page(
        metric: RankingMetric,
        period: RankingPeriod,
        page: Int,
        size: Int,
    ): PageResponse<RankingEntry> {
        val entries = rankingRepository.findPage(metric, period, size, page * size)
        val total = rankingRepository.countRanked(period).toLong()
        return PageResponse(entries, page, size, total, ((total + size - 1) / size).toInt())
    }

    /** 그 사용자의 순위. 푼 문제가 없거나 랭킹을 껐으면 null — 꼴찌가 아니라 **순위가 없는** 것이다. */
    fun rankOf(nickname: String, metric: RankingMetric, period: RankingPeriod): RankingEntry? =
        rankingRepository.findRankOf(nickname, metric, period)
}
