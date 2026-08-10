package codekr.api.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 어드민이 조정할 수 있는 실행기 배포의 범위 (#40).
 *
 * 상한을 두는 이유는 실수로 200 replica 를 요청해 노드를 마비시키는 일을 막기 위함이다.
 */
@ConfigurationProperties(prefix = "codekr.executor-scaling")
data class ExecutorScalingProperties(
    val deployment: String = "codekr-executor",
    /**
     * 실행기가 있는 네임스페이스. 비우면 api 자신의 네임스페이스를 쓴다.
     *
     * 실행기는 런타임 소켓을 마운트하느라 Pod Security 가 느슨한 별도 네임스페이스에
     * 놓일 수 있어서(차트의 `executor.namespace`), 같은 곳에 있다고 가정하지 않는다.
     */
    val namespace: String = "",
    val minReplicas: Int = 1,
    val maxReplicas: Int = 20,
)
