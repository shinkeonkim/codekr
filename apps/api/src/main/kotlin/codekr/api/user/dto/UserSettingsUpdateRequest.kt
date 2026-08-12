package codekr.api.user.dto

import codekr.api.submission.entity.SubmissionVisibility
import codekr.api.user.entity.UserTheme

/**
 * 설정 변경 (#104).
 *
 * 값이 null 인 항목은 **바꾸지 않는다.** 항목이 늘어도 화면이 전체를 보내지 않아도 되게
 * 하기 위함이다 — 전체를 보내게 하면 새 항목이 생길 때 옛 화면이 그것을 지운다.
 */
data class UserSettingsUpdateRequest(
    val defaultSubmissionVisibility: SubmissionVisibility? = null,
    /**
     * 화면 테마 (#274).
     *
     * **보낸 항목만 바꾼다**(#104)는 규칙 때문에 "고른 적 없음" 으로 되돌릴 수는 없다 —
     * 지금 화면에 그 선택지가 없고, 있다면 `SYSTEM` 이 같은 뜻을 한다.
     */
    val theme: UserTheme? = null,
)
