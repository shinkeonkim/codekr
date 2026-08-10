package codekr.api.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "codekr.jwt")
data class JwtProperties(
    val secret: String,
    val accessTtlSeconds: Long = 3600,
    val refreshTtlSeconds: Long = 1_209_600,
)
