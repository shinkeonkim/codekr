package codekr.api.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "codekr.submission")
data class SubmissionProperties(
    val maxSourceCodeBytes: Int = 65_536,
    /** 이 시간이 지나도 채점이 끝나지 않은 제출은 SYSTEM_ERROR 로 종결한다. */
    val staleTimeoutSeconds: Long = 180,
)
