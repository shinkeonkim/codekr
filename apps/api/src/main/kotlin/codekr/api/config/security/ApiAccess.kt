package codekr.api.config.security

import codekr.api.user.entity.UserRole

/**
 * 엔드포인트의 접근 수준을 **컨트롤러에 선언한다** (#198).
 *
 * 전에는 한 엔드포인트의 접근 수준이 세 곳에 나뉘어 있었고 세 곳 모두 경로를 문자열로
 * 다시 적었다 — 컨트롤러의 매핑, `SecurityConfig` 의 패턴, 그리고 손으로 적는 목록.
 * **경로가 바뀌어도 나머지 둘은 컴파일된다.**
 *
 * 여기 붙는 애노테이션은 **선언이지 집행이 아니다.** 집행은 지금처럼 필터 체인에서,
 * 본문 바인딩 **전에** 일어난다 — `@PreAuthorize` 를 쓰지 않는 이유가 그것이다.
 * 메서드 보안은 본문 바인딩 뒤에 돌아서, 권한 없는 요청이 400(검증 실패)을 먼저 받고
 * 막혔다는 사실조차 알 수 없다.
 *
 * **자원 소유권은 여기서 표현되지 않는다.** "로그인하면 누구나"로 선언된 뒤 *자기
 * 것인지*는 서비스가 따로 본다. 대회 관리자가 자기 대회만 만지는 것(#103)도 마찬가지다.
 */
sealed interface ApiAccessLevel {
    /** 토큰 없이 누구나. */
    data object Public : ApiAccessLevel

    /** 로그인한 사용자면 누구나. */
    data object Authenticated : ApiAccessLevel

    /** 이 역할 **이상**. 위계는 `RoleHierarchy` 가 잇는다 (#103). */
    data class Role(val role: UserRole) : ApiAccessLevel
}

/** 토큰 없이 누구나 부를 수 있다. */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class PublicApi

/**
 * 로그인하면 누구나 부를 수 있다.
 *
 * **자기 자원인지까지 보장하지 않는다.** 그 검사는 서비스가 한다.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class AuthenticatedApi

/** 이 역할 이상만 부를 수 있다 (#103). */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class AdminApi(val role: UserRole)

/**
 * 선언에서 끌어낸 규칙 하나. (HTTP 메서드, 경로) 단위다.
 *
 * **패턴이 아니라 정확한 경로다.** 전에는 어드민 문제 경로의 와일드카드 규칙이 어드민
 * 전체 규칙보다 **먼저 와야 한다**는 순서 의존이 있었고, 새 규칙을 잘못된 자리에 끼우면
 * 조용히 덮였다.
 *
 * (주석에 와일드카드 경로를 그대로 적지 않는다 — 코틀린은 블록 주석이 중첩되어
 * 경로 안의 슬래시+별표가 새 주석을 연다.)
 */
data class ApiRule(val method: String, val pattern: String, val level: ApiAccessLevel)
