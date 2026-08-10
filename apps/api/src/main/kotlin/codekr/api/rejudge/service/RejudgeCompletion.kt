package codekr.api.rejudge.service

/**
 * 재채점 결과 마감 (#107).
 *
 * 인터페이스로 둔 이유는 순환 의존을 끊기 위함이다 — 채점 결과를 기록하는 쪽(submission)은
 * 재채점을 알 필요가 없고, 재채점 쪽은 제출을 읽어야 한다.
 */
interface RejudgeCompletion {
    /** 판정이 바뀌었으면 그 사용자에게 알린다. */
    fun completeOne(submissionId: Long)
}
