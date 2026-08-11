package codekr.api.rejudge.dto

/**
 * 재채점을 누르기 전에 알아야 할 것 (#219).
 *
 * **몇 명에게 알림이 갈지 모른 채 누르게 하면 안 된다.** 재채점은 대상이었던 모든
 * 회원에게 알림을 보내고(#187), 그것은 되돌릴 수 없는 바깥 방향 동작이다.
 *
 * `latest` 는 마지막 배치다. 아직 돌고 있으면 화면이 그 진행 상황을 보여준다 —
 * 끝나지 않은 것을 또 누르면 같은 사람에게 알림이 두 번 간다.
 */
data class RejudgeStatusResponse(
    val problemId: Long,
    /** 지금 누르면 다시 채점될 제출 수. 정답 검증 제출(#39)은 세지 않는다. */
    val targetCount: Int,
    val latest: RejudgeResponse?,
)
