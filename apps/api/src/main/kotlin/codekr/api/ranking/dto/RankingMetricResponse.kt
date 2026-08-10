package codekr.api.ranking.dto

import codekr.api.ranking.entity.RankingMetric

data class RankingMetricResponse(val value: String, val label: String, val description: String) {
    companion object {
        fun from(metric: RankingMetric) =
            RankingMetricResponse(metric.name, metric.label, metric.description)
    }
}
