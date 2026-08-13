package codekr.api.stats

import codekr.api.config.security.PublicApi
import codekr.api.runtime.RuntimeRegistry
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 첫 화면이 보여 줄 숫자 (#231).
 *
 * **부끄럽지 않은 것만 고른다.** 실제보다 작아 보이는 숫자는 안 보이느니만 못하다 —
 * 회원 수·제출 수는 초기에 비어 보이므로 내리지 않는다. 문제 수와 지원 언어 수는
 * 처음부터 말이 되는 숫자다.
 *
 * 비로그인이 보는 화면이므로 공개다.
 */
@RestController
@RequestMapping("/api/v1/stats/site")
class SiteStatsController(
    private val jdbcClient: JdbcClient,
    private val runtimeRegistry: RuntimeRegistry,
) {

    @PublicApi
    @GetMapping
    fun site(): SiteStatsResponse = SiteStatsResponse(
        // 공개된 문제만 센다. 준비 중인 것을 세면 눌렀을 때 없는 숫자가 된다.
        problemCount = jdbcClient
            .sql("SELECT count(*) FROM problems WHERE published = true AND deleted_at IS NULL")
            .query(Int::class.java)
            .single(),
        runtimeCount = runtimeRegistry.findAll().size,
    )
}

data class SiteStatsResponse(val problemCount: Int, val runtimeCount: Int)
