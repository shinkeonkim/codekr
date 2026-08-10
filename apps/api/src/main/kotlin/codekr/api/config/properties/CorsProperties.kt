package codekr.api.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "codekr.cors")
data class CorsProperties(
    /** 쉼표로 구분된 허용 오리진 목록. 와일드카드는 사용하지 않는다. */
    val allowedOrigins: List<String> = listOf("http://localhost:13000"),
)
