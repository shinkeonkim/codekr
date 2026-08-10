package codekr.api.scaling.service

import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.config.properties.ExecutorScalingProperties
import codekr.api.scaling.dto.ExecutorScaleStatus
import org.springframework.stereotype.Service

/**
 * 어드민의 실행기 스케일 제어 (#40).
 *
 * 채점 큐가 밀릴 때 워커를 늘리는 것이 목적이다. 큐 적체는 어드민 큐 모니터링 화면에서
 * 이미 보이므로, 같은 화면에서 바로 대응할 수 있게 한다.
 */
@Service
class ExecutorScaleService(
    private val client: ExecutorScaleClient,
    private val properties: ExecutorScalingProperties,
) {

    fun status(): ExecutorScaleStatus {
        if (!client.available) return unavailable()

        val (desired, ready) = runCatching { client.read(properties.deployment) }
            .getOrElse { return unavailable("실행기 배포 상태를 읽지 못했습니다.") }

        return ExecutorScaleStatus(
            available = true,
            deployment = properties.deployment,
            desiredReplicas = desired,
            readyReplicas = ready,
            minReplicas = properties.minReplicas,
            maxReplicas = properties.maxReplicas,
        )
    }

    /**
     * replica 를 조정한다. 허용 범위 밖이면 거부한다 —
     * 실수로 큰 수를 넣어 노드를 마비시키는 일을 막는다.
     */
    fun scale(replicas: Int): ExecutorScaleStatus {
        if (!client.available) throw ApiException(ErrorCode.SCALING_UNAVAILABLE)
        if (replicas < properties.minReplicas || replicas > properties.maxReplicas) {
            throw ApiException(
                ErrorCode.VALIDATION_ERROR,
                "실행기 수는 ${properties.minReplicas}~${properties.maxReplicas} 사이여야 합니다.",
            )
        }

        client.scale(properties.deployment, replicas)
        return status()
    }

    private fun unavailable(reason: String? = null) = ExecutorScaleStatus(
        available = false,
        deployment = properties.deployment,
        desiredReplicas = 0,
        readyReplicas = 0,
        minReplicas = properties.minReplicas,
        maxReplicas = properties.maxReplicas,
        reason = reason ?: client.unavailableReason,
    )
}
