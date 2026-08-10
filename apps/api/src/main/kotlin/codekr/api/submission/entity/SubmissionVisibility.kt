package codekr.api.submission.entity

/**
 * 제출 소스 코드의 공개 범위.
 *
 * **메타데이터(문제·판정·실행 시간)와 소스 코드를 분리해서 다룬다.** 전체 제출 목록은
 * 누구의 제출이 어떤 판정을 받았는지 보여주지만, 소스 코드는 이 값에 따라 가려진다.
 */
enum class SubmissionVisibility {
    /** 판정과 관계없이 소스 코드를 공개한다. */
    PUBLIC,

    /** 작성자와 관리자에게만 공개한다. 새 제출의 기본값이다. */
    PRIVATE,

    /**
     * 최종 판정이 ACCEPTED 일 때만 공개한다.
     * 채점이 끝나기 전이나 오답일 때는 비공개다 — 풀이 도중의 코드가 새지 않는다.
     */
    ACCEPTED_ONLY,
}
