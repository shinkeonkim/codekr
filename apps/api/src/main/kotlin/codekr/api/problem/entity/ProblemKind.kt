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
     * **유형마다 "무엇을 채점하는가" 가 다르다.** SQL 은 정답 쿼리이고 Redis 은 끝난
     * 뒤의 상태다. 이 사실을 쓰는 곳(제출·저장 검증)마다 유형 이름을 나열하면 유형이
     * 하나 늘 때 그중 하나를 빠뜨리게 되고, 그 경로에서만 조용히 막힌다 —
     * 실제로 SQL 하나를 나열하던 자리가 둘 있었다.
     */
    val needsTestcases: Boolean = true,
    /**
     * 정답 코드 검증(#39)을 할 수 있는 유형인가 (#495).
     *
     * 그 기능이 하는 일은 **정답 코드를 테스트케이스에 돌려 보는 것**이다. SQL 의
     * 대응물은 "정답 쿼리를 스키마에 돌려 본다" 이고 Redis 은 "정답 명령을 시드에
     * 돌려 상태를 본다" 이다 — **유형마다 "검증한다" 의 뜻이 다르다.** 그것을 갖추기
     * 전까지는 **할 수 없다고 말하는 편**이 테스트케이스가 없다고 말하는 것보다 낫다.
     */
    val supportsSolutionVerification: Boolean = true,
) {
    /** 소스 코드를 실행해 stdout 을 기대 출력과 비교한다 (ADR-0006). 지금 있는 모든 문제. */
    JUDGE_STDIO("코드 실행 (stdin/stdout)", ready = true),

    /** 격리 PostgreSQL 에서 쿼리를 실행해 결과 집합을 비교한다 (#60). */
    JUDGE_SQL("SQL", ready = true, needsTestcases = false, supportsSolutionVerification = false),

    /**
     * 격리 Redis 에서 명령의 연속을 실행해 **끝난 뒤의 상태**를 비교한다 (#455).
     *
     * SQL 과 나눈 이유: 제출이 쿼리 하나가 아니라 명령의 연속이고, 정답이 결과 집합이
     * 아니라 상태다. 같은 유형에 두면 "무엇이 정답인가" 가 문제마다 달라진다.
     */
    JUDGE_REDIS("Redis", ready = true, needsTestcases = false, supportsSolutionVerification = false),

    /**
     * 격리 MongoDB 에서 스크립트를 돌려 **끝난 뒤를 확인 스크립트로 읽는다** (#527).
     *
     * Redis 와 나눈 이유: **질의 언어가 아예 다르다.** #454 가 SQL 에 MariaDB 를
     * 더할 때는 런타임만 얹으면 됐지만(같은 언어) 여기는 스펙 표부터 갈린다.
     *
     * 이름을 좁게 둔다 — #455 가 "NoSQL" 이라는 넓은 이름 때문에 **낼 수 없는 문제를
     * 낼 수 있다고 읽히던 것**을 겪었다.
     */
    JUDGE_MONGODB("MongoDB", ready = true, needsTestcases = false, supportsSolutionVerification = false),

    /**
     * 함수만 구현하는 문제 (#421). **하네스가 입출력을 맡는다.**
     *
     * 사용자에게는 함수 껍데기만 보이고, 보이지 않는 하네스가 입력을 읽어 그 함수를
     * 부르고 결과를 찍는다. `JUDGE_STDIO` 에 얹지 않은 이유는 **"하네스가 있으면
     * 함수형" 이라는 암묵 규칙**을 만들지 않기 위해서다 — 그러면 하네스를 지웠을 때
     * 문제의 성격이 조용히 바뀐다.
     */
    JUDGE_FUNCTION("함수 구현", ready = true),

    /**
     * 채점 코드와 **도는 중에 주고받는** 문제 (#474).
     *
     * 스페셜 저지(#452)와 나눈 이유: 그쪽은 끝난 뒤 출력을 받아 판정하고 이쪽은
     * 동시에 돌며 주고받는다. 실행 방식이 다르므로 유형이 다르다.
     *
     * 정답 코드 검증(#39)은 아직 없다 — 검증도 대화를 붙여야 하는 일이라 따로 본다.
     */
    JUDGE_INTERACTIVE("인터랙티브", ready = true, supportsSolutionVerification = false),

    /**
     * 객관식·단답 (#650). **실행기를 쓰지 않는 유일한 유형이다.**
     *
     * 채점이 값 비교라 api 가 그 자리에서 한다 — 그래서 런타임도, 시간·메모리 제한도,
     * 테스트케이스도 없다. 정답 코드 검증(#39)의 대응물도 없다: 검증한다는 것이
     * "정답 코드를 돌려 본다" 인데 여기에는 돌릴 것이 없다.
     *
     * **점수는 난이도에서 나온다.** 퀴즈는 난이도를 두지 않아(`UNRATED`) 0점이고,
     * 푼 문제 수와 활동에는 센다 — #195 가 이미 정해 둔 규칙을 그대로 쓴다.
     * 찍어서 맞는 문제가 랭킹 합에 들어가지 않게 하려는 것이고, 그 판단을 랭킹
     * 계산에 손대지 않고 얻는다.
     */
    QUIZ("객관식 · 단답", ready = true, needsTestcases = false, supportsSolutionVerification = false),

    /** 서술형. 사람이 검수한다. */
    MANUAL("서술형 (사람 검수)", ready = false),
    ;

    companion object {
        val SELECTABLE: List<ProblemKind> = entries.filter { it.ready }
    }
}
