package codekr.api.admin.service

import codekr.api.config.properties.DataResetProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component

/**
 * "전부 지우기" 를 걷을 날을 놓치지 않기 위한 장치 (#303).
 *
 * #285 가 이 기능을 만들면서 **스스로 걷을 조건**을 적었다 — "실제 사용자의 제출이
 * 하나라도 쌓이는 날". 그런데 조건을 문서에만 두면 **아무도 치우지 않는다.**
 * 그 조건을 여기서 실제로 재고, 넘으면 뜰 때마다 경고한다.
 *
 * **막지는 않는다.** 켜고 끄는 것은 배포 값의 몫이고, 여기서 거부하면 로컬에서
 * 시드를 넣고 비우는 일이 갑자기 안 된다 — 그것이 지금 이 기능의 쓰임이다.
 */
@Component
class DataResetSunset(
    private val properties: DataResetProperties,
    private val jdbcClient: JdbcClient,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun warnIfLive() {
        if (!properties.enabled) return

        // 시드로 넣은 제출(#280)과 사람이 낸 제출을 여기서 가르지 못한다 — 둘 다 kind
        // 가 USER 다. 그래서 "지운 적 없는 제출이 이만큼 있다" 를 그대로 보인다.
        val submissions = runCatching {
            jdbcClient.sql("SELECT count(*) FROM submissions WHERE kind = 'USER'")
                .query(Long::class.java).single()
        }.getOrElse { return }

        if (submissions >= THRESHOLD) {
            log.warn(
                "데이터 초기화(전부 지우기)가 켜져 있는데 사용자 제출이 {}건 있습니다. " +
                    "운영을 시작했다면 CODEKR_DATA_RESET_ENABLED 를 내리세요 (#303).",
                submissions,
            )
        }
    }

    private companion object {
        /**
         * 이 수를 넘으면 경고한다.
         *
         * 0 으로 두면 시드 문제 몇 개를 풀어 본 로컬에서도 매번 뜬다 — 늘 뜨는 경고는
         * 읽히지 않는다. 손으로 눌러 본 수를 넘어서면 그때는 사람이 쓰고 있는 것이다.
         */
        const val THRESHOLD = 50L
    }
}
