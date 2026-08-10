package codekr.api.contest.entity

/**
 * 대회의 진행 단계 (#61).
 *
 * **저장하지 않는다.** 시작·종료 시각과 지금을 견줘 조회 시점에 판정한다 —
 * 스케줄러가 상태를 옮기는 방식이면 스케줄러가 1분 늦는 순간 대회가 1분 늦게
 * 시작한다. 지연이 곧 사고가 된다.
 */
enum class ContestPhase(val label: String) {
    /** 준비 중. 어드민만 본다. */
    DRAFT("준비 중"),

    /** 공개됐고 아직 시작 전. 참가 등록은 되지만 문제는 감춰진다. */
    SCHEDULED("시작 전"),

    RUNNING("진행 중"),

    ENDED("종료"),

    /** 종료 후 보존 단계. 연습 제출만 받는다. */
    ARCHIVED("보관"),

    CANCELED("취소됨"),
    ;

    /** 문제를 참가자에게 보여도 되는 단계인가. */
    val problemsVisible: Boolean
        get() = this == RUNNING || this == ENDED || this == ARCHIVED

    /** 대회 제출을 받는 단계인가. 연습 제출(#62)은 별개다. */
    val acceptsSubmission: Boolean get() = this == RUNNING
}
