package codekr.api.user.dto

import codekr.api.auth.email.MailOutcome
import java.time.Instant

/**
 * 어드민이 인증에 손댄 결과 (#524).
 *
 * **보냈다는 사실과 갔다는 사실은 다르다.** [mail] 이 그 차이를 말한다 — 지금까지
 * 발송 실패는 로그에만 남아서, 누른 사람은 성공과 실패를 구분할 수 없었다.
 */
data class AdminEmailVerificationResponse(
    val userId: Long,
    /** 인증된 시각. 재발송만 했다면 여전히 `null` 이다. */
    val emailVerifiedAt: Instant?,
    /** 메일을 보낸 경우에만 있다. 강제 인증은 메일을 보내지 않는다. */
    val mail: MailOutcome? = null,
) {
    /** 화면이 그대로 보여 줄 한 줄. 서버가 정한다 — 세 곳이 다르게 말하면 안 된다. */
    val message: String = when {
        emailVerifiedAt != null -> "인증 처리했습니다."
        mail == MailOutcome.SENT -> "인증 메일을 보냈습니다."
        mail == MailOutcome.SKIPPED -> "메일 설정이 없어 보내지 않았습니다. 서버 로그에 링크가 있습니다."
        else -> "메일 발송에 실패했습니다. 서버 로그를 확인하세요."
    }
}
