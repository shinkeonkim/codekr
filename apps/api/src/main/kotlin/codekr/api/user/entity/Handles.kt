package codekr.api.user.entity

/**
 * 주소가 되는 이름의 규칙 (#307).
 *
 * **가입과 백필이 같은 규칙을 쓴다.** 두 곳에 적으면 갈리고, 갈리면 백필로 만들어진
 * 값이 가입 검증을 통과하지 못하는 상태가 생긴다.
 */
object Handles {

    const val MIN_LENGTH = 2
    const val MAX_LENGTH = 30

    /** 소문자·숫자·하이픈만. 대문자를 허용하면 `Kim` 과 `kim` 이 다른 주소가 된다. */
    private val PATTERN = Regex("^[a-z0-9][a-z0-9-]{1,29}$")

    fun isValid(handle: String): Boolean = PATTERN.matches(handle)

    /**
     * 이름에서 주소를 만든다.
     *
     * 한글·공백·특수문자는 그대로 주소가 될 수 없다 — 남는 것이 없으면 부르는 쪽이
     * 대신 정하게 null 을 돌려준다.
     */
    fun from(source: String): String? =
        source.lowercase().replace(Regex("[^a-z0-9-]"), "").takeIf { it.length >= MIN_LENGTH }
            ?.take(MAX_LENGTH)
}
