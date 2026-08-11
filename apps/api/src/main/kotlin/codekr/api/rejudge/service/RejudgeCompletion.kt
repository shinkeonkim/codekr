package codekr.api.rejudge.service

/**
 * 재채점 결과 마감 (#107).
 *
 * 인터페이스로 둔 이유는 순환 의존을 끊기 위함이다 — 채점 결과를 기록하는 쪽(submission)은
 * 재채점을 알 필요가 없고, 재채점 쪽은 제출을 읽어야 한다.
 */
interface RejudgeCompletion {
    /**
     * 판정이 바뀌었으면 그 사용자에게 알린다.
     *
     * @param scoreDelta 이 제출로 인한 랭킹 점수 변화량 (#57). 점수가 내려갔다는 사실은
     *   판정이 바뀌었다는 사실만큼 중요하므로 같은 알림에 담는다.
     */
    fun completeOne(submissionId: Long, scoreDelta: Int)
}
