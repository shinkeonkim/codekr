package codekr.api.board.mention

import codekr.api.user.entity.User
import codekr.api.user.entity.WithdrawnUser

/**
 * 본문에 담기는 멘션 표기 (#214).
 *
 * **`@닉네임` 을 글자 그대로 저장하지 않는다.** 사용자 id 를 담아 두면 세 가지가
 * 한꺼번에 풀린다.
 *
 * | 문제 | 왜 풀리는가 |
 * |---|---|
 * | 닉네임 경계(`@김 철수`) | 저장된 것이 이름이 아니라 **본문에서 이름을 잘라 낼 일이 없다** |
 * | 닉네임 변경 | id 가 가리키는 사람은 그대로다. 표시만 새 이름이 된다 |
 * | 탈퇴(#140) | 다른 곳과 같은 규칙으로 "탈퇴한 사용자" 가 된다 |
 *
 * 중괄호를 쓰는 이유: 본문이 마크다운인데 **대괄호는 링크 문법과 겹친다.**
 * 중괄호는 마크다운에서 아무 뜻이 없다.
 */
object Mentions {

    private val PATTERN = Regex("""@\{u:(\d+)}""")

    /**
     * 한 댓글에서 부를 수 있는 인원.
     *
     * **알림은 끌 수 없다** (#199) — 부르면 반드시 간다. 상한이 없으면 한 줄로 수십
     * 명에게 알림을 보낼 수 있다.
     */
    const val MAX_PER_BODY = 5

    /** 본문이 가리키는 사용자 id. 같은 사람을 여러 번 불러도 한 번이다. */
    fun idsIn(body: String): List<Long> =
        PATTERN.findAll(body).mapNotNull { it.groupValues[1].toLongOrNull() }.distinct().toList()

    fun of(user: User) = MentionResponse(user.id, WithdrawnUser.nicknameOf(user))
}

/**
 * 화면이 표기를 이름으로 바꾸는 데 쓰는 값 (#214).
 *
 * **본문과 함께 내려야 한다.** 화면이 id 로 사람을 다시 조회하면 댓글 수만큼 요청이
 * 나가고, 그 사이에 하나라도 실패하면 멘션이 표기 그대로 보인다.
 */
data class MentionResponse(val id: Long, val nickname: String)
