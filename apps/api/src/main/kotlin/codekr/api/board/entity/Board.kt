package codekr.api.board.entity

import codekr.api.user.entity.UserRole

/**
 * 게시판 (#137).
 *
 * **셋으로 시작한다.** 나중에 늘리기는 쉽지만 합치기는 어렵다 — 글이 쌓인 뒤에 합치면
 * 그 글들이 어디로 가야 하는지 사람이 하나씩 정해야 한다.
 */
enum class Board(val label: String, val description: String, val writeRole: UserRole?) {
    FREE("자유", "무엇이든", writeRole = null),
    QUESTION("질문", "막힌 곳을 묻습니다", writeRole = null),

    /**
     * 공지. **운영자만 쓴다.**
     *
     * 아무나 쓸 수 있으면 공지가 아니다 — 읽는 사람이 "이건 반드시 읽어야 하는 것" 이라고
     * 믿을 수 있어야 공지판이 쓸모가 있다.
     */
    NOTICE("공지", "운영자 알림", writeRole = UserRole.BOARD_MANAGER),
    ;
}
