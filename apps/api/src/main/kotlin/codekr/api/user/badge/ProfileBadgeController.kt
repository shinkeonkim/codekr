package codekr.api.user.badge

import codekr.api.config.security.PublicApi
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

/**
 * 프로필 배지 (#475).
 *
 * ## 왜 `/api/v1` 아래가 아닌가
 *
 * **사람이 손으로 적어 README 에 붙이는 주소**다. 우리 화면이 부르는 API 가 아니라
 * 이미지 한 장이고, 짧을수록 붙여 넣기 쉽다. `/api/v1` 은 우리가 언제든 v2 로 옮길 수
 * 있어야 하는 자리인데, **남의 README 에 박힌 주소는 우리가 옮길 수 없다.**
 *
 * ## 캐시가 이 기능의 전부다
 *
 * GitHub 은 이미지를 자기 프록시로 받아 오래 잡는다.
 *
 * - 길게 주면 푼 문제 수가 **며칠 전 것**으로 남는다
 * - 짧게 주면 README 를 여는 사람 수만큼 **우리 서버가 맞는다** — 인기가 있을수록 그렇다
 *
 * 10분으로 둔다. 배지에 실리는 숫자는 분 단위로 바뀌는 값이 아니고, 프록시가 그보다
 * 오래 잡더라도 우리가 "이 정도면 낡지 않았다" 고 말한 값은 남는다.
 * `stale-while-revalidate` 를 함께 줘서, 캐시가 만료돼도 **다시 받아오는 동안 옛 그림을
 * 보여 주게** 한다 — 그 사이에 깨진 이미지가 보이는 것이 가장 나쁘다.
 *
 * ## 검색에 뜰 이유가 없다
 *
 * `X-Robots-Tag: noindex` 를 붙인다 (#278). 배지는 사람이 읽는 문서가 아니다.
 */
@RestController
class ProfileBadgeController(
    private val badgeService: ProfileBadgeService,
    private val rateLimiter: BadgeRateLimiter,
) {

    @PublicApi
    // **문자 집합을 적는다.** 배지에 한글이 들어가는데(닉네임·"연속"), 적지 않으면
    // 보는 쪽이 제 인코딩으로 읽어 글자가 깨진다.
    @GetMapping("/badge/{handle}.svg", produces = ["image/svg+xml;charset=UTF-8"])
    fun badge(
        @PathVariable handle: String,
        /** `dark` 를 주면 어두운 배경용 그림이 온다 (#475). 모르는 값은 밝은 쪽이다. */
        @RequestParam(required = false) theme: String?,
        request: HttpServletRequest,
    ): ResponseEntity<String> {
        // 로그인이 없으므로 주소로 센다 (#475).
        if (!rateLimiter.allow(request.remoteAddr ?: "unknown")) {
            // **429 에 짧은 캐시를 준다.** 프록시가 이 응답을 오래 잡으면 정상으로
            // 돌아온 뒤에도 배지가 안 보인다.
            return ResponseEntity.status(429)
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(30)).cachePublic())
                .build()
        }
        val svg = badgeService.render(handle, BadgeTheme.of(theme))
        return ResponseEntity.ok()
            .contentType(MediaType.valueOf("image/svg+xml;charset=UTF-8"))
            .cacheControl(
                CacheControl.maxAge(Duration.ofMinutes(10))
                    .cachePublic()
                    .staleWhileRevalidate(Duration.ofDays(1)),
            )
            .header("X-Robots-Tag", "noindex")
            .body(svg)
    }
}
