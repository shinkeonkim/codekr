package codekr.api.user.controller

import codekr.api.auth.security.AuthPrincipal
import codekr.api.config.security.PublicApi
import codekr.api.user.dto.UserProfileResponse
import codekr.api.ranking.service.ScoreHistoryService
import codekr.api.ranking.service.ScorePoint
import codekr.api.user.service.UserProfileService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserProfileController(
    private val userProfileService: UserProfileService,
    private val scoreHistoryService: ScoreHistoryService,
) {

    /**
     * 점수가 어떻게 변해 왔는가 (#476).
     *
     * **활동 그래프(#117)와 같은 자리에 있다.** 그쪽은 "얼마나 자주 했는가" 이고
     * 이것은 "얼마나 늘었는가" 다 — 배우는 사람에게는 오르는 것이 보이는 것이
     * 계속하는 이유가 된다.
     *
     * 로그인 없이 열린다. 점수·티어·순위는 랭킹(#57)이 이미 공개한다.
     */
    @PublicApi
    @GetMapping("/{handle}/score-history")
    fun scoreHistory(
        @PathVariable handle: String,
        @RequestParam(defaultValue = "365") days: Int,
    ): List<ScorePoint> = scoreHistoryService.of(handle, days)

    /**
     * 회원 프로필 (#83, #333).
     *
     * **로그인 없이 열린다.** 전에는 아니었고, 그래서 게시판·랭킹·문제집에 걸린 이름을
     * 비로그인이 누르면 로그인 화면으로 튕겼다 — **누르면 튕기는 링크는 고장으로 보인다**
     * (#131 이 어드민 메뉴에서 같은 판단을 했다).
     *
     * **제출 목록(#34)을 함께 열지는 않는다.** 그쪽은 지금도 로그인이 필요하고, 그것이
     * 우회되지 않는 이유는 이 화면이 주는 것이 **센 숫자**이지 "누가 어떤 문제를 언제
     * 냈는지" 가 아니기 때문이다. 푼 문제 수·점수·순위는 랭킹(#57)이 이미 공개한다.
     *
     * 여기서 새로 공개되는 것은 소개 문구(#310)·연속 기록·태그별 분포·뱃지다.
     * 소개 문구는 **순수 글자 100자에 링크가 열리지 않으므로**(#310) 공개를 전제로
     * 이미 판단된 값이다.
     */
    @PublicApi
    @GetMapping("/{handle}")
    fun findProfile(
        @PathVariable handle: String,
        // 보는 사람이 있으면 문제집 진행률이 채워진다 (#209). 없어도 목록은 그대로다.
        principal: AuthPrincipal?,
    ): UserProfileResponse = userProfileService.findByHandle(handle, principal?.userId)
}
