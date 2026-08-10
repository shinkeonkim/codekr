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
)
