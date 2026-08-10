package codekr.api.submission.entity

/**
 * 제출의 종류.
 *
 * 정답 코드 검증도 사용자 제출과 같은 채점 파이프라인을 쓴다. 파이프라인을 두 벌 만들지 않으려는
 * 선택이고, 대신 **사용자에게 보이는 모든 경로에서 검증 제출을 걸러내야 한다.**
 */
enum class SubmissionKind {
    /** 사용자가 문제를 풀어 낸 제출. */
    USER,

    /** 어드민이 정답 코드로 테스트케이스를 검증하려고 만든 제출. */
    SOLUTION_VERIFICATION,
}
