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
enum class ProblemKind(
    val label: String,
    val ready: Boolean,
    /**
     * 채점 대상이 테스트케이스인지 (#455).
     *
     * **유형마다 "무엇을 채점하는가" 가 다르다.** SQL 은 정답 쿼리이고 NoSQL 은 끝난
     * 뒤의 상태다. 이 사실을 쓰는 곳(제출·저장 검증)마다 유형 이름을 나열하면 유형이
     * 하나 늘 때 그중 하나를 빠뜨리게 되고, 그 경로에서만 조용히 막힌다 —
     * 실제로 SQL 하나를 나열하던 자리가 둘 있었다.
     */
    val needsTestcases: Boolean = true,
) {
    /** 소스 코드를 실행해 stdout 을 기대 출력과 비교한다 (ADR-0006). 지금 있는 모든 문제. */
    JUDGE_STDIO("코드 실행 (stdin/stdout)", ready = true),

    /** 격리 PostgreSQL 에서 쿼리를 실행해 결과 집합을 비교한다 (#60). */
    JUDGE_SQL("SQL", ready = true, needsTestcases = false),

    /**
     * 격리 Redis 에서 명령의 연속을 실행해 **끝난 뒤의 상태**를 비교한다 (#455).
     *
     * SQL 과 나눈 이유: 제출이 쿼리 하나가 아니라 명령의 연속이고, 정답이 결과 집합이
     * 아니라 상태다. 같은 유형에 두면 "무엇이 정답인가" 가 문제마다 달라진다.
     */
    JUDGE_NOSQL("NoSQL", ready = true, needsTestcases = false),

    /** 객관식·단답. 실행기를 쓰지 않고 api 에서 즉시 채점한다. */
    QUIZ("객관식 · 단답", ready = false),

    /** 서술형. 사람이 검수한다. */
    MANUAL("서술형 (사람 검수)", ready = false),
    ;

    companion object {
        val SELECTABLE: List<ProblemKind> = entries.filter { it.ready }
    }
}
