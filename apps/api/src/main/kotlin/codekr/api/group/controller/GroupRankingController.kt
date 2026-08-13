package codekr.api.group.controller

import codekr.api.auth.security.AuthPrincipal
import codekr.api.common.dto.PageResponse
import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.config.security.AuthenticatedApi
import codekr.api.group.repository.GroupMemberRepository
import codekr.api.group.service.GroupService
import codekr.api.ranking.dto.RankingEntry
import codekr.api.ranking.entity.RankingMetric
import codekr.api.ranking.entity.RankingPeriod
import codekr.api.ranking.service.RankingService
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 그룹 안 랭킹 (#402, #240 7단계).
 *
 * **소속 안 랭킹(#399)과 같은 구조다** — 모집단을 좁힐 뿐 정렬을 바꾸지 않는다. 등수는
 * 그 안에서 1위부터 다시 매겨진다: "우리 스터디에서 2등" 이 이 기능의 이유다.
 *
 * 다른 것은 **누가 볼 수 있느냐**다. 소속 랭킹은 공개지만 **그룹은 멤버만 본다** —
 * 그룹의 명단이 곧 그 랭킹이고, 명단은 그 안의 일이라고 #401 이 정했다. 공개로 두면
 * 그룹 id 하나로 누가 있는지 전부 읽을 수 있다.
 *
 * 그래서 경로도 `/rankings?groupId=` 가 아니라 그룹 밑이다 — **접근 규칙이 다른 것을
 * 같은 경로의 질의 인자로 두면 그 규칙이 잊힌다.**
 */
@RestController
@RequestMapping("/api/v1/groups/{groupId}/rankings")
@Validated
class GroupRankingController(
    private val rankingService: RankingService,
    private val groupService: GroupService,
    private val members: GroupMemberRepository,
) {

    @AuthenticatedApi
    @GetMapping
    fun list(
        @PathVariable groupId: Long,
        @RequestParam(defaultValue = "SCORE") metric: RankingMetric,
        @RequestParam(defaultValue = "ALL_TIME") period: RankingPeriod,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "50") @Min(1) @Max(100) size: Int,
        principal: AuthPrincipal,
    ): PageResponse<RankingEntry> {
        // 없는 그룹이면 404 가 먼저다 — 해산한 그룹의 순위는 없다.
        groupService.find(groupId)
        if (!members.existsByGroupIdAndUserId(groupId, principal.userId)) {
            throw ApiException(ErrorCode.FORBIDDEN, "그룹의 멤버만 볼 수 있습니다.")
        }
        return rankingService.page(metric, period, page, size, groupId = groupId)
    }
}
