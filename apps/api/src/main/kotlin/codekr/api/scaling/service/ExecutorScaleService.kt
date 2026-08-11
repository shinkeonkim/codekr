package codekr.api.scaling.service

import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.config.properties.ExecutorScalingProperties
import codekr.api.scaling.dto.ExecutorScaleState
import codekr.api.scaling.dto.ExecutorScaleStatus
import org.slf4j.LoggerFactory
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

    private val log = LoggerFactory.getLogger(javaClass)

    fun status(): ExecutorScaleStatus {
        if (!client.available) return degraded(ExecutorScaleState.OUTSIDE_CLUSTER, client.unavailableReason)

        val (desired, ready) = try {
            client.read(properties.deployment)
        } catch (error: ScaleAccessException) {
            // 자세한 내용은 클라이언트가 이미 남겼다. 여기서는 무엇으로 분류됐는지만.
            return degraded(ExecutorScaleState.UNREADABLE, error.failure.message)
        } catch (error: Exception) {
            log.error("실행기 배포 상태 조회가 예상 못 한 이유로 실패했습니다", error)
            return degraded(ExecutorScaleState.UNREADABLE, ScaleAccessFailure.UNKNOWN.message)
        }

        return ExecutorScaleStatus(
            state = ExecutorScaleState.OK,
            deployment = properties.deployment,
            namespace = client.namespace,
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

    /**
     * 수를 모르는 상태.
     *
     * **조정까지 막지는 않는다** (#237). 읽기 권한이 없어도 `scale` 권한은 있을 수 있고,
     * 그때 화면 전체를 잠그면 고칠 수단까지 사라진다.
     */
    private fun degraded(state: ExecutorScaleState, reason: String?) = ExecutorScaleStatus(
        state = state,
        deployment = properties.deployment,
        namespace = client.namespace,
        desiredReplicas = 0,
        readyReplicas = 0,
        minReplicas = properties.minReplicas,
        maxReplicas = properties.maxReplicas,
        reason = reason,
    )
}
