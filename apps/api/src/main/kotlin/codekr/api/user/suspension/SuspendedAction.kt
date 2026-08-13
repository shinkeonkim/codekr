package codekr.api.user.suspension

/**
 * 이 요청이 무엇을 하려는 것인가 (#224).
 *
 * **선언이 아니라 요청에서 읽는다.** 핸들러마다 표시하게 하면(#198 의 접근 수준처럼)
 * 새 엔드포인트마다 하나씩 더 적어야 하고, 빠뜨린 것은 정지가 통하지 않는 구멍이
 * 된다. 규칙은 이 파일 하나로 끝난다.
 */
enum class SuspendedAction {
    /** 안전한 메서드. 정지는 읽기를 막지 않는다. */
    NONE,
    WRITE,
    SUBMIT,
    ;

    companion object {
        private val SAFE_METHODS = setOf("GET", "HEAD", "OPTIONS")

        /**
         * 정지 중에도 되어야 하는 쓰기.
         *
         * - 로그인·로그아웃·토큰 갱신: 막으면 **자기가 왜 막혔는지 볼 수도 없다**
         * - 스스로 탈퇴: 정지가 떠날 자유까지 뺏지는 않는다
         * - 자기 설정·알림 읽음 표시: 남에게 보이지 않는, 자기 것에 대한 조작이다
         */
        private val ALLOWED_PREFIXES = listOf(
            "/api/v1/auth/",
            "/api/v1/users/me/settings",
            "/api/v1/notifications",
        )

        /** 채점기를 쓰는 것. 제출과 예제 실행은 같은 자원을 먹는다. */
        private val SUBMIT_SUFFIXES = listOf("/submissions", "/run")

        fun of(method: String, path: String): SuspendedAction = when {
            method.uppercase() in SAFE_METHODS -> NONE
            ALLOWED_PREFIXES.any { path.startsWith(it) } -> NONE
            path == "/api/v1/users/me" && method.equals("DELETE", ignoreCase = true) -> NONE
            SUBMIT_SUFFIXES.any { path.endsWith(it) } -> SUBMIT
            else -> WRITE
        }
    }
}
