package codekr.api.runtime

import codekr.api.problem.entity.ProblemKind

/** 실행 환경 하나의 화면용 정보. 실제 실행 방법(이미지·명령)은 실행기만 알면 된다. */
data class RuntimeDefinition(
    val id: String,
    val label: String,
    val monacoLanguage: String,
    val template: String,
    /**
     * 이 런타임으로 풀 수 있는 문제 유형 (#60).
     *
     * SQL 런타임이 알고리즘 문제의 선택지로 새면, 사용자는 고를 수 있지만 채점은
     * 되지 않는 조합을 만나게 된다. **선택지 자체를 유형으로 가른다.**
     */
    val problemKind: ProblemKind = ProblemKind.JUDGE_STDIO,
    /**
     * 이 런타임이 **기동에 쓰는 시간** (#454).
     *
     * 문제의 시간 제한은 컨테이너 전체에 걸린다 — DB 를 띄우는 시간도 그 안이다.
     * MariaDB 는 3.5초, PostgreSQL 은 0.5초다. 이것을 모르면 출제자는 "쿼리는 순식간인데
     * 무엇을 내도 시간 초과" 를 만나고, 그 이유를 짐작할 방법이 없다.
     */
    val startupMs: Int = 0,
    /**
     * 함수만 구현하는 문제를 지원하는가 (#421).
     *
     * 정의 파일에 `functionHarness` 가 있는 런타임만이다 — **실행기와 같은 파일을 본다.**
     * 두 곳이 따로 정하면 "고를 수 있는데 돌지 않는" 조합이 생긴다.
     */
    val supportsFunctionHarness: Boolean = false,
) {
    /**
     * 이 런타임으로 그 유형의 문제를 풀 수 있는가 (#59, #421).
     *
     * **규칙을 한 곳에 둔다.** 화면이 고를 목록과 서버가 제출을 받을 조건이 다른 곳에서
     * 정해지면, "고를 수는 있는데 채점되지 않는" 조합이 생긴다.
     *
     * 함수형 문제(#421)만 규칙이 다르다 — 그 유형을 선언한 런타임이 아니라 **하네스를
     * 얹을 수 있는 언어**가 후보다. 파이썬은 stdin/stdout 문제도 함수형 문제도 푼다.
     */
    fun canSolve(kind: ProblemKind): Boolean =
        if (kind == ProblemKind.JUDGE_FUNCTION) supportsFunctionHarness else problemKind == kind
}
