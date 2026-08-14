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
     * 이 런타임으로 **함수형 문제**를 낼 수 있는가 (#446, #421).
     *
     * 실행기가 하네스와 사용자 코드를 어떻게 나눠 놓고 돌릴지 아는 런타임만 참이다
     * (`runtimes.yaml` 의 `functionHarness`). 모르면 하네스를 줘도 돌릴 수 없다.
     */
    val supportsFunctionHarness: Boolean = false,
    /**
     * 이 런타임이 **기동에 쓰는 시간** (#454).
     *
     * 문제의 시간 제한은 컨테이너 전체에 걸린다 — DB 를 띄우는 시간도 그 안이다.
     * MariaDB 는 3.5초, PostgreSQL 은 0.5초다. 이것을 모르면 출제자는 "쿼리는 순식간인데
     * 무엇을 내도 시간 초과" 를 만나고, 그 이유를 짐작할 방법이 없다.
     */
    val startupMs: Int = 0,
) {
    /**
     * 이 런타임으로 그 유형의 문제를 풀 수 있는가 (#60, #446).
     *
     * **함수형은 자기 런타임을 따로 갖지 않는다.** 파이썬 문제를 파이썬으로 푸는 것은
     * 같고, 다른 것은 하네스가 진입점을 가져간다는 것뿐이다.
     *
     * 규칙을 여기 한 곳에 둔다 — 목록을 거르는 곳과 제출을 막는 곳이 다른 규칙을 쓰면
     * **화면에 보이는데 제출은 안 되는** 조합이 생긴다.
     */
    fun canSolve(kind: ProblemKind): Boolean = when (kind) {
        ProblemKind.JUDGE_FUNCTION -> problemKind == ProblemKind.JUDGE_STDIO && supportsFunctionHarness
        else -> problemKind == kind
    }
}
