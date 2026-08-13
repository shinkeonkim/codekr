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

    /**
     * 순위표 한 쪽.
     *
     * [affiliationId] 를 주면 **그 소속 사람들만** 본다 (#399). [groupId] 는 그룹에
     * 같은 일을 한다 (#402). 모집단을 좁히는 것이라 등수는 그 안에서 1위부터 다시
     * 매겨진다 — "우리 학교에서 3등", "우리 스터디에서 2등" 이 이 기능의 이유다.
     */
    fun page(
        metric: RankingMetric,
        period: RankingPeriod,
        page: Int,
        size: Int,
        affiliationId: Long? = null,
        groupId: Long? = null,
    ): PageResponse<RankingEntry> {
        val entries = rankingRepository.findPage(metric, period, size, page * size, affiliationId, groupId)
        val total = rankingRepository.countRanked(period, affiliationId, groupId).toLong()
        return PageResponse(entries, page, size, total, ((total + size - 1) / size).toInt())
    }

    /** 그 사용자의 순위. 푼 문제가 없거나 랭킹을 껐으면 null — 꼴찌가 아니라 **순위가 없는** 것이다. */
    fun rankOf(
        nickname: String,
        metric: RankingMetric,
        period: RankingPeriod,
        affiliationId: Long? = null,
    ): RankingEntry? = rankingRepository.findRankOf(nickname, metric, period, affiliationId)
}
