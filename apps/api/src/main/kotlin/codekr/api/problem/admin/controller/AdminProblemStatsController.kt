package codekr.api.problem.admin.controller

import codekr.api.config.security.AdminApi
import codekr.api.problem.repository.ProblemStatsSyncRepository
import codekr.api.problem.repository.StatsDrift
import codekr.api.user.entity.UserRole
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 저장된 문제 통계를 다시 만들고, 어긋났는지 본다 (#205).
 *
 * **저장하기로 한 이상 이 두 경로가 반드시 있어야 한다** — 랭킹(#177)에서 이미 정한
 * 규칙이다. 값이 어긋났을 때 되돌릴 방법이 없으면 애초에 저장하면 안 된다.
 *
 * 접근 수준은 컨트롤러에 선언한다 (#198). 문제를 만드는 일의 일부라 PROBLEM_SETTER 다.
 */
@RestController
@RequestMapping("/api/v1/admin/problems/stats")
class AdminProblemStatsController(private val statsSync: ProblemStatsSyncRepository) {

    /** 전부 다시 센다. 이 기능을 처음 켤 때도 필요하다 — 그 전의 제출은 갱신 경로를 지나지 않았다. */
    @AdminApi(UserRole.PROBLEM_SETTER)
    @PostMapping("/recompute")
    fun recompute(): Map<String, Int> = mapOf("problems" to statsSync.refreshAll())

    /**
     * 저장된 값과 지금 세어 본 값이 다른 문제들.
     *
     * **어긋나도 아무도 모르는 것이 가장 나쁘다.** 갱신 경로를 하나 빠뜨렸을 때
     * 그것을 알아낼 방법이 이것뿐이다.
     */
    @AdminApi(UserRole.PROBLEM_SETTER)
    @GetMapping("/drift")
    fun drift(): List<StatsDrift> = statsSync.findDrift()
}
