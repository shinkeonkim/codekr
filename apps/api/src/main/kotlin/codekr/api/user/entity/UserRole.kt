package codekr.api.user.entity

/**
 * 사용자 역할 (#103).
 *
 * 한 사람이 여러 역할을 가질 수 있다 — 문제도 내고 게시판도 보는 사람이 있다.
 * 그래서 `users.role` 한 칸이 아니라 `user_roles` 표로 둔다.
 *
 * **모두 전역 역할이다.** "이 대회의 관리자" 처럼 자원마다 다른 권한은 아직 없다.
 * 대회 도메인이 생길 때 함께 만든다 (#61) — 지금 자원 범위를 설계하면 쓰이지 않는
 * 추상을 만들게 되고, 대회의 실제 요구를 보기 전이라 틀릴 가능성이 크다.
 */
enum class UserRole {
    /** 일반 회원. 모든 계정이 기본으로 가진다. */
    USER,

    /**
     * 최고 관리자. 역할 부여를 포함해 전부 할 수 있다.
     *
     * ADMIN 과 나눈 이유: 역할을 주고 뺏는 일은 되돌리기 어렵고, 그 권한을 가진 사람이
     * 많아지면 누가 무엇을 줬는지 추적이 안 된다.
     */
    SUPERUSER,

    /** 운영 관리자. 인프라·큐·보관 정책 등 서비스 운영. */
    ADMIN,

    /** 문제 출제자. 문제를 만들고 검증한다. 인프라는 만지지 않는다. */
    PROBLEM_SETTER,

    /**
     * 대회 관리자.
     *
     * 지금은 전역이다. **"자기가 맡은 대회만" 은 #61 에서 다룬다** —
     * 대회 도메인 자체가 없어서 범위를 지정할 대상이 없다.
     */
    CONTEST_MANAGER,

    /** 게시판 관리자. 게시판 도메인이 생기면 쓰인다 (아직 해당 API 없음). */
    BOARD_MANAGER,
    ;

    companion object {
        /** 어드민 영역에 들어올 수 있는 역할. USER 만 가진 사람은 못 들어온다. */
        val ADMIN_AREA: Set<UserRole> =
            setOf(SUPERUSER, ADMIN, PROBLEM_SETTER, CONTEST_MANAGER, BOARD_MANAGER)
    }
}
