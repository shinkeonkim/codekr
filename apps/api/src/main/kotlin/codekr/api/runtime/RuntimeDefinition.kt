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
)
