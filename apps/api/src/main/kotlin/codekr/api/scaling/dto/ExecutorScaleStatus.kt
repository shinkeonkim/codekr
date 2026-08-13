package codekr.api.scaling.dto

/**
 * 실행기 배포의 현재 상태.
 *
 * [state] 가 세 가지인 이유 (#237): 전에는 "클러스터 밖이라 못 한다" 와 "읽기에 실패했다"
 * 가 같은 `available = false` 로 내려갔다. **앞은 설정이 그런 것이고 뒤는 고장이다** —
 * 화면이 다르게 말해야 하는데 구분할 수단이 없었다.
 */
data class ExecutorScaleStatus(
    /** 조정할 때 경로에 쓰는 이름 (#390). 설정의 허용 목록 키다. */
    val key: String,
    /** 사람이 읽는 이름 — 실행기·채점기·대회 채점기. */
    val label: String,
    val state: ExecutorScaleState,
    val deployment: String,
    /** 보고 있는 네임스페이스. 클러스터 밖이면 null. */
    val namespace: String?,
    val desiredReplicas: Int,
    val readyReplicas: Int,
    val minReplicas: Int,
    val maxReplicas: Int,
    /**
     * 지금 정해진 워커 수 (#390). 채점기에만 있다.
     *
     * **null 은 "정한 적이 없다" 다.** 그때 채점기는 기동할 때 읽은 값을 쓴다 —
     * 0 으로 내려보내면 화면이 "워커가 없다" 로 읽는다.
     */
    val workers: Int? = null,
    val reason: String? = null,
) {
    /** 조정 버튼을 보일지. **읽기 실패와 별개다** — 권한이 scale 에만 있어도 조정은 된다. */
    val controllable: Boolean get() = state != ExecutorScaleState.OUTSIDE_CLUSTER
}

enum class ExecutorScaleState {
    /** 클러스터 밖(로컬 docker compose 등)이다. 오류가 아니라 설정이다. */
    OUTSIDE_CLUSTER,

    /** 클러스터 안인데 상태를 읽지 못했다. **고장이다.** */
    UNREADABLE,

    OK,
}
