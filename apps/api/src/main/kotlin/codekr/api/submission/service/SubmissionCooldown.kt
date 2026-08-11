package codekr.api.submission.service

import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import java.time.Duration
import java.time.Instant

/**
 * 같은 문제를 다시 낼 수 있기까지의 간격 (#189).
 *
 * **자동화된 반복 제출이 채점 큐를 채우는 것을 막는다.** 대회에서는 그것이 곧 다른
 * 참가자의 채점 지연이고, 마감이 있어 나중에 처리하는 것으로 해결되지 않는다.
 *
 * 대회와 일반 제출이 **같은 판정 코드**를 쓴다 — 두 곳에 따로 쓰면 메시지와 경계 조건이
 * 갈라진다.
 */
object SubmissionCooldown {

    /** 일반 제출 간격. 고치고 다시 내는 정상 흐름은 막지 않는 선이다. */
    val DEFAULT: Duration = Duration.ofSeconds(30)

    /**
     * 대회가 완화할 수 있는 하한.
     *
     * **0 을 허용하지 않는다.** 제한이 없는 것과 같아지고, 그러면 한 참가자가 채점 차선을
     * 혼자 채울 수 있다.
     */
    val MINIMUM: Duration = Duration.ofSeconds(3)

    /**
     * 마지막 제출이 [cooldown] 안이면 거절한다.
     *
     * @param lastSubmittedAt 같은 사람·같은 문제의 마지막 제출 시각. 없으면 통과한다.
     */
    fun require(lastSubmittedAt: Instant?, cooldown: Duration, now: Instant) {
        if (lastSubmittedAt == null) return

        val elapsed = Duration.between(lastSubmittedAt, now)
        if (elapsed >= cooldown) return

        // 남은 시간을 알려 준다 — 그냥 거절하면 고장으로 보인다.
        // 올림해서 알린다: 0.2초 남았는데 "0초 뒤" 라고 하면 다시 눌러도 또 막힌다.
        val remaining = cooldown.minus(elapsed).plusMillis(999).seconds
        throw ApiException(
            ErrorCode.SUBMISSION_TOO_FREQUENT,
            "같은 문제는 ${cooldown.seconds}초에 한 번 낼 수 있습니다. ${remaining}초 뒤에 다시 시도하십시오.",
        )
    }

    /** 대회가 정한 값을 하한에 맞춰 잡는다. 하한보다 짧게 설정돼 있어도 하한을 지킨다. */
    fun ofSeconds(seconds: Int): Duration = maxOf(Duration.ofSeconds(seconds.toLong()), MINIMUM)
}
