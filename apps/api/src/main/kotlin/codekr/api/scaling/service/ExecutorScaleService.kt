package codekr.api.scaling.service

import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.audit.entity.AdminAction
import codekr.api.audit.service.AdminAuditService
import codekr.api.config.properties.ScalingProperties
import codekr.api.config.properties.ScalingTarget
import org.springframework.transaction.annotation.Transactional
import codekr.api.scaling.dto.ExecutorScaleState
import codekr.api.scaling.dto.ExecutorScaleStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 어드민의 워크로드 조정 (#40, #390).
 *
 * 채점 큐가 밀릴 때 늘리는 것이 목적이다. 큐 적체는 어드민 큐 모니터링 화면에서 이미
 * 보이므로, 같은 화면에서 바로 대응할 수 있게 한다.
 *
 * **막히는 곳이 다르면 늘릴 것도 다르다** (#390). 실행이 느리면 실행기를 늘리지만,
 * **채점기가 큐를 못 빼면 실행기를 늘려도 소용없다** — 일을 꺼내는 쪽이 부족한 것이다.
 * 그래서 대상이 셋이고, 대상마다 파드 수와 (채점기는) 워커 수를 따로 조정한다.
 */
@Service
class ExecutorScaleService(
    private val client: ExecutorScaleClient,
    private val properties: ScalingProperties,
    private val workerRegistry: JudgeWorkerRegistry,
    private val auditService: AdminAuditService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 조정할 수 있는 것 전부. 화면이 무엇이 있는지 서버에게 묻는다. */
    fun statuses(): List<ExecutorScaleStatus> = properties.targets.keys.sorted().map { status(it) }

    /** 화면에 보일 이름. 오류 문구도 이것으로 대상을 가린다 (#431). */
    private fun label(key: String, target: ScalingTarget): String = target.label.ifBlank { key }

    private fun require(key: String): ScalingTarget =
        properties.target(key) ?: throw ApiException(ErrorCode.SCALING_UNAVAILABLE)

    fun status(key: String): ExecutorScaleStatus {
        val target = require(key)
        if (!client.available) return degraded(key, target, ExecutorScaleState.OUTSIDE_CLUSTER, client.unavailableReason)

        val (desired, ready) = try {
            client.read(target.deployment, target.namespace)
        } catch (error: ScaleAccessException) {
            // 자세한 내용은 클라이언트가 이미 남겼다. 여기서는 무엇으로 분류됐는지만.
            return degraded(key, target, ExecutorScaleState.UNREADABLE, error.failure.messageFor(label(key, target)))
        } catch (error: Exception) {
            log.error("배포 상태 조회가 예상 못 한 이유로 실패했습니다: {}", target.deployment, error)
            return degraded(
                key,
                target,
                ExecutorScaleState.UNREADABLE,
                ScaleAccessFailure.UNKNOWN.messageFor(label(key, target)),
            )
        }

        return ExecutorScaleStatus(
            key = key,
            label = target.label.ifBlank { key },
            state = ExecutorScaleState.OK,
            deployment = target.deployment,
            namespace = target.namespace.ifBlank { client.namespace },
            desiredReplicas = desired,
            readyReplicas = ready,
            minReplicas = target.minReplicas,
            maxReplicas = target.maxReplicas,
            workers = workers(target),
        )
    }

    /**
     * replica 를 조정한다. 허용 범위 밖이면 거부한다 —
     * 실수로 큰 수를 넣어 노드를 마비시키는 일을 막는다.
     */
    @Transactional
    fun scale(actorId: Long, key: String, replicas: Int): ExecutorScaleStatus {
        val target = require(key)
        if (!client.available) throw ApiException(ErrorCode.SCALING_UNAVAILABLE)
        if (replicas < target.minReplicas || replicas > target.maxReplicas) {
            throw ApiException(
                ErrorCode.VALIDATION_ERROR,
                "${target.label.ifBlank { key }} 수는 ${target.minReplicas}~${target.maxReplicas} 사이여야 합니다.",
            )
        }

        client.scale(target.deployment, replicas, target.namespace)
        record(actorId, key, "파드 수", replicas)
        return status(key)
    }

    /**
     * 워커 수를 바꾼다 (#390).
     *
     * **재시작하지 않는다.** `JUDGE_CONCURRENCY` 는 기동할 때 한 번 읽는 값이라 전에는
     * 배포를 다시 해야 했는데, **늘리려는 상황이 곧 재시작하면 안 되는 상황이다** —
     * 진행 중인 채점이 끊긴다. 채점기가 이 값을 주기적으로 읽어 스스로 맞춘다.
     *
     * 상한은 채점기가 자기 기동값을 기준으로 다시 한 번 자른다. 여기서 막는 것은
     * **화면의 실수**이고, 거기서 막는 것은 **잘못된 값이 흘러들어온 경우**다.
     */
    @Transactional
    fun setWorkers(actorId: Long, key: String, workers: Int): ExecutorScaleStatus {
        val target = require(key)
        if (!target.adjustsWorkers) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "이 대상은 워커 수를 조정하지 않습니다.")
        }
        if (workers < MIN_WORKERS || workers > MAX_WORKERS) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "워커 수는 $MIN_WORKERS~$MAX_WORKERS 사이여야 합니다.")
        }

        workerRegistry.write(target.lane, workers)
        record(actorId, key, "워커 수", workers)
        return status(key)
    }

    /** 지금 정해진 워커 수. 정한 적이 없으면 채점기가 기동값을 쓰므로 null 이다. */
    private fun workers(target: ScalingTarget): Int? =
        target.takeIf { it.adjustsWorkers }?.let { workerRegistry.read(it.lane) }

    /** **누가 무엇을 몇으로 바꿨는지 남긴다** (#225). 전에는 실행기 조정도 기록이 없었다. */
    private fun record(actorId: Long, key: String, what: String, value: Int) {
        auditService.record(
            actorId = actorId,
            action = AdminAction.SCALE,
            targetId = 0,
            targetLabel = key,
            detail = "$what → $value",
        )
    }

    /**
     * 수를 모르는 상태.
     *
     * **조정까지 막지는 않는다** (#237). 읽기 권한이 없어도 `scale` 권한은 있을 수 있고,
     * 그때 화면 전체를 잠그면 고칠 수단까지 사라진다.
     */
    private fun degraded(key: String, target: ScalingTarget, state: ExecutorScaleState, reason: String?) =
        ExecutorScaleStatus(
            key = key,
            label = target.label.ifBlank { key },
            state = state,
            deployment = target.deployment,
            namespace = target.namespace.ifBlank { client.namespace },
            desiredReplicas = 0,
            readyReplicas = 0,
            minReplicas = target.minReplicas,
            maxReplicas = target.maxReplicas,
            workers = workers(target),
            reason = reason,
        )

    private companion object {
        const val MIN_WORKERS = 1
        const val MAX_WORKERS = 64
    }
}
