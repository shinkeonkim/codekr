package codekr.api.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 데이터 초기화 기능의 스위치 (#285).
 *
 * **기본은 꺼짐이다.** 이것은 관리 화면에 "전부 지우기" 버튼을 다는 일이라, 켜는 것이
 * 명시적인 선택이어야 한다. 배포에서 값을 안 주면 없는 기능이 된다.
 *
 * ## 언제 걷는가
 *
 * **운영을 시작하는 날 끈다.** 지금 이 사이트는 스테이징에 가깝고, 기능을 붙이고 시험
 * 데이터를 넣고 다시 비우는 일이 반복된다. 실제 사용자의 제출이 하나라도 쌓이는 순간
 * 이 버튼은 사고를 기다리는 장치가 된다.
 *
 * 끄는 순서: 배포 값에서 `enabled` 를 내리고 → 화면에서 사라지는 것을 확인하고 →
 * 다음 정리에서 코드를 걷는다.
 */
@ConfigurationProperties(prefix = "codekr.data-reset")
data class DataResetProperties(
    val enabled: Boolean = false,
)
