package codekr.api.scaling.dto

/**
 * 실행기 배포의 현재 상태.
 *
 * [available] 이 false 면 클러스터 밖(로컬 docker compose 등)에서 도는 것이다 —
 * 오류가 아니라 상태이므로 화면이 안내 문구를 보여줄 수 있게 이유를 함께 담는다.
 */
data class ExecutorScaleStatus(
    val available: Boolean,
    val deployment: String,
    val desiredReplicas: Int,
    val readyReplicas: Int,
    val minReplicas: Int,
    val maxReplicas: Int,
    val reason: String? = null,
)
