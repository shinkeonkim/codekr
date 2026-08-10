package codekr.api.notification.entity

/**
 * 알림 카테고리 (#106).
 *
 * 종류별로 끄고 켤 수 있어야 한다. 하나로 뭉치면 필요한 알림까지 끄게 된다.
 */
enum class NotificationCategory(val label: String, val mutable: Boolean) {
    /** 재채점으로 내 제출 결과가 바뀜 등 (#107). */
    JUDGE("채점", mutable = true),

    /** 대회 공지, 질의 답변 (#63). */
    CONTEST("대회", mutable = true),

    /**
     * 내 공개 코드를 누가 읽었는지 (#136).
     *
     * 채점에 얹지 않고 따로 둔 이유: 채점 알림은 **내 기록이 바뀐 것**이고 이것은
     * **남이 내 것을 본 것**이다. 뜻이 다르면 끄고 켜는 단위도 달라야 한다.
     */
    SUBMISSION_VIEW("코드 열람", mutable = true),

    /**
     * 점검·정책 변경. **끌 수 없다.**
     *
     * 서비스가 사용자에게 반드시 전해야 하는 것만 여기 넣는다. 끌 수 있게 하면
     * "안 알려줬다" 와 "껐다" 를 구분할 수 없어진다.
     */
    SYSTEM("시스템", mutable = false),
    ;

    companion object {
        val MUTABLE: List<NotificationCategory> = entries.filter { it.mutable }
    }
}
