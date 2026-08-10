package codekr.api.ranking.controller

import codekr.api.common.dto.PageResponse
import codekr.api.ranking.dto.RankingEntry
import codekr.api.ranking.dto.RankingMetricResponse
import codekr.api.ranking.entity.RankingMetric
import codekr.api.ranking.service.RankingService
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 랭킹 조회 (#57, #85). 누구나 볼 수 있다 — 랭킹은 공개 정보다. */
@RestController
@RequestMapping("/api/v1/rankings")
@Validated
class RankingController(private val rankingService: RankingService) {

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "SCORE") metric: RankingMetric,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "50") @Min(1) @Max(100) size: Int,
    ): PageResponse<RankingEntry> = rankingService.page(metric, page, size)

    /**
     * 지표 목록. **화면이 지표를 하드코딩하지 않게 서버가 알려준다** —
     * 지표가 늘어날 때 화면을 같이 고쳐야 하는 구조를 만들지 않는다.
     */
    @GetMapping("/metrics")
    fun metrics(): List<RankingMetricResponse> = RankingMetric.entries.map(RankingMetricResponse::from)
}
