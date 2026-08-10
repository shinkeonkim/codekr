package codekr.api.problem.entity

/**
 * 문제 유형 (#59).
 *
 * 유형마다 **풀이 입력·실행 환경·정답 표현·비교 방식·보안 경계**가 다르다.
 * `ProblemCategory`(알고리즘/SQL/네트워크…)와는 다른 축이다 — 카테고리는 무엇에 대한
 * 문제인지를, 유형은 어떻게 채점하는지를 말한다.
 *
 * `ready = false` 인 유형은 **아직 채점할 수 없다.** 스펙 테이블도 채점기 구현도 없다.
 * 어드민이 고를 수 있게 두면 채점되지 않는 문제가 만들어진다.
 */
enum class ProblemKind(val label: String, val ready: Boolean) {
    /** 소스 코드를 실행해 stdout 을 기대 출력과 비교한다 (ADR-0006). 지금 있는 모든 문제. */
    JUDGE_STDIO("코드 실행 (stdin/stdout)", ready = true),

    /** 격리 PostgreSQL 에서 쿼리를 실행해 결과 집합을 비교한다 (#60). */
    JUDGE_SQL("SQL", ready = true),

    /** 객관식·단답. 실행기를 쓰지 않고 api 에서 즉시 채점한다. */
    QUIZ("객관식 · 단답", ready = false),

    /** 서술형. 사람이 검수한다. */
    MANUAL("서술형 (사람 검수)", ready = false),
    ;

    companion object {
        val SELECTABLE: List<ProblemKind> = entries.filter { it.ready }
    }
}
