package codekr.api.config

import codekr.api.activity.ActivityPolicy
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * "오늘이 며칠인가" 를 정하는 시계 (#241).
 *
 * **시간대를 시계가 들고 다닌다.** 날짜를 판단하는 곳마다 `LocalDate.now(ZONE)` 이라고
 * 쓰면, 한 곳만 빠뜨려도 그 자리만 서버 기본 시간대를 따르고 아무도 눈치채지 못한다.
 * 실제로 시험 쪽이 그렇게 어긋나 있었다 — DB 의 `current_date` 는 접속한 JVM 의 기본
 * 시간대를 따르는데, 앱은 서울 기준으로 물었다.
 *
 * **주입해서 쓰는 이유는 얼릴 수 있어야 하기 때문이다.** 날짜 경계에서만 나는 문제는
 * 하루 중 언제 시험이 도는지에 따라 결과가 달라진다. 시계를 고정하면 그 순간을 골라
 * 재현할 수 있다: 한국 자정 30분은 UTC 로는 **전날** 오후 3시 30분이다.
 */
@Configuration
class ClockConfig {

    @Bean
    fun clock(): Clock = Clock.system(ActivityPolicy.ZONE)
}
