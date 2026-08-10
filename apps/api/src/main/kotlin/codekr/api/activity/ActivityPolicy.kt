package codekr.api.activity

import java.time.ZoneId

/**
 * 활동 집계 정책 (#36). 결정 근거는 docs/08_활동_스트릭_정책.md 에 있다.
 */
object ActivityPolicy {

    /**
     * 하루의 경계를 정하는 시간대.
     *
     * UTC 로 자르면 한국 시간 오전 9시 이전 제출이 전날로 잡혀, 사용자가 체감하는 "오늘"과
     * 어긋난다. 서비스 대상이 한국어권이므로 고정한다.
     */
    val ZONE: ZoneId = ZoneId.of("Asia/Seoul")

    /** 활동 그래프가 기본으로 보여주는 기간(일). */
    const val DEFAULT_RANGE_DAYS = 365L

    /** 한 번에 조회할 수 있는 최대 기간(일). */
    const val MAX_RANGE_DAYS = 366L * 3
}
