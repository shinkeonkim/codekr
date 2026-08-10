package codekr.api.problem.entity

enum class TestcaseVisibility {
    /** 문제 상세에 예제로 공개된다. */
    PUBLIC,

    /** 채점에만 쓰이며 어떤 공개 API 응답에도 포함되지 않는다. */
    HIDDEN,
}
