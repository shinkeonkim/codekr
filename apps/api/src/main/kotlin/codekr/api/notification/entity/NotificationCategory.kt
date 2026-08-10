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
