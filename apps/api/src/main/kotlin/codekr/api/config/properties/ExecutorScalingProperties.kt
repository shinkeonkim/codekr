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
    val minReplicas: Int = 1,
    val maxReplicas: Int = 20,
)
