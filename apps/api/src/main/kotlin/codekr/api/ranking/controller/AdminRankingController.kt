package codekr.api.ranking.controller

import codekr.api.user.entity.UserRole
import codekr.api.config.security.AdminApi
import codekr.api.ranking.service.RankingRecomputeService
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 랭킹 집계 재계산 (#177).
 *
 * 활동 집계(#105)와 같은 자리에 같은 모양으로 둔다 — 두 집계가 같은 성격이므로
 * 운영하는 사람이 규칙을 두 벌 외우지 않아도 되게 한다.
 *
 * 경로 규칙에 따로 적지 않았으므로 최고 관리자만 부를 수 있다 (#103).
 */
@RestController
@RequestMapping("/api/v1/admin")
class AdminRankingController(private val recomputeService: RankingRecomputeService) {

    @AdminApi(UserRole.SUPERUSER)
    @PostMapping("/users/{userId}/ranking/recompute")
    fun recompute(@PathVariable userId: Long): Map<String, Int> {
        val result = recomputeService.recompute(userId)
        return mapOf("score" to result.score, "solvedCount" to result.solvedCount)
    }

    /**
     * 맞힌 제출이 있는 모든 사용자를 다시 계산한다.
     *
     * **랭킹 기능을 처음 붙인 직후에 한 번 불러야 한다.** 그 전의 제출은 점수 표를
     * 거치지 않았으므로, 부르지 않으면 랭킹이 빈 채로 시작한다.
     */
    @AdminApi(UserRole.SUPERUSER)
    @PostMapping("/ranking/recompute")
    fun recomputeEveryone(): Map<String, Int> =
        mapOf("users" to recomputeService.recomputeEveryone())
}
