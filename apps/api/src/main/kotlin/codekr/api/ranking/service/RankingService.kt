package codekr.api.ranking.service

import codekr.api.common.dto.PageResponse
import codekr.api.ranking.dto.RankingEntry
import codekr.api.ranking.entity.RankingMetric
import codekr.api.ranking.repository.RankingRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class RankingService(private val rankingRepository: RankingRepository) {

    fun page(metric: RankingMetric, page: Int, size: Int): PageResponse<RankingEntry> {
        val entries = rankingRepository.findPage(metric, size, page * size)
        val total = rankingRepository.countRanked().toLong()
        return PageResponse(entries, page, size, total, ((total + size - 1) / size).toInt())
    }

    /** 그 사용자의 순위. 푼 문제가 없으면 null — 꼴찌가 아니라 **순위가 없는** 것이다. */
    fun rankOf(nickname: String, metric: RankingMetric): RankingEntry? =
        rankingRepository.findRankOf(nickname, metric)
}
