package codekr.api.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 소프트 삭제된 행의 보관 정책 (#46).
 *
 * 대상마다 보관 기간이 다른 이유는 **되살릴 이유가 있는 기간**이 다르기 때문이다.
 * 제출 이력은 사용자의 기록이라 지우지 않고, 문제 편집 부산물은 오래 둘 이유가 없다.
 */
@ConfigurationProperties(prefix = "codekr.retention")
data class RetentionProperties(
    val enabled: Boolean = true,

    /** 삭제된 문제를 실제로 지우기까지의 유예. 잘못 지운 문제를 되살릴 시간을 준다. */
    val problemDays: Long = 90,

    /**
     * 문제를 수정할 때마다 세대가 쌓이는 테스트케이스·초기 코드.
     * 되살릴 이유가 거의 없어 짧게 둔다.
     */
    val problemChildDays: Long = 30,

    /**
     * 읽은 알림을 보관하는 기간 (#106).
     *
     * **안 읽은 알림은 지우지 않는다.** 읽지 않았다는 것은 아직 전달되지 않았다는 뜻이다.
     */
    val readNotificationDays: Long = 90,

    /** 한 번에 지울 최대 행 수. 배치가 DB 를 오래 붙잡지 않게 한다. */
    val batchSize: Int = 500,
)
