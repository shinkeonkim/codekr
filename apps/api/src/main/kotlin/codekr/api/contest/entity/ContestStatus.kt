package codekr.api.contest.entity

/**
 * 운영자가 정하는 상태 (#61).
 *
 * 진행 단계([ContestPhase])와 나눈 이유: 진행 단계는 **시각이 정하고**, 이것은
 * **사람이 정한다.** 한 값에 섞으면 "취소된 진행 중 대회" 같은 것을 표현할 수 없다.
 */
enum class ContestStatus {
    DRAFT,
    PUBLISHED,
    CANCELED,
    ARCHIVED,
}
