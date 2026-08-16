package codekr.api.problem.entity

/**
 * 문제의 분야 — **무엇에 대한 문제인가** (#5).
 *
 * `ProblemKind`(어떻게 채점하는가)와 다른 축이고, 태그(#232)와도 다르다 —
 * 태그는 "어떤 기법으로 푸는가" 다.
 *
 * ## 제품이 분야가 되기도 한다
 *
 * `SQL`·`REDIS`·`MONGODB` 는 유형에도 같은 이름이 있다. 두 축이 겹쳐 보이지만
 * **묻는 것이 다르다** — 유형은 채점기가 무엇을 하는지이고, 분야는 배우는 사람이
 * 무엇을 고르는지다. 그 둘이 일대일이어도 고르는 자리는 분야 쪽이다.
 */
enum class ProblemCategory(val label: String) {
    ALGORITHM("알고리즘"),
    DATA_STRUCTURE("자료구조"),
    SQL("SQL"),

    /**
     * Redis (#455). MongoDB 와 **묶지 않는다** — 유형에서 나눈 것과 같은 이유다.
     *
     * "NoSQL" 로 묶으면 그 이름이 할 수 있는 것보다 넓어져, 고른 사람이 기대한 것과
     * 다른 문제를 만난다.
     */
    REDIS("Redis"),

    /** MongoDB (#527). 질의 언어가 Redis 와 아예 달라 배우는 것도 다르다. */
    MONGODB("MongoDB"),

    /**
     * 셸 스크립트 (#456).
     *
     * `LANGUAGE` 와 나눈 이유: 셸 문제가 묻는 것은 **문법이 아니라 `awk`·`sort`·파이프로
     * 일을 엮는 것**이다. 언어를 배우는 것과 도구를 엮는 것은 다른 연습이다.
     */
    SHELL("셸 스크립트"),

    NETWORK("네트워크"),
    LANGUAGE("프로그래밍 언어"),
    OS("운영체제"),
    SYSTEM_DESIGN("시스템 설계"),
}
