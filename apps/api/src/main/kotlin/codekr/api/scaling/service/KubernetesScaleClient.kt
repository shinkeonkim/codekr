package codekr.api.scaling.service

import codekr.api.config.properties.ExecutorScalingProperties
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import tools.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path

/**
 * 파드에 주입된 ServiceAccount 자격증명으로 쿠버네티스 API 를 호출한다.
 *
 * 클라이언트 라이브러리를 새로 들이지 않는 이유는, 쓰는 API 가 **Deployment 하나의 scale**
 * 뿐이기 때문이다. 무거운 의존성보다 HTTP 호출 두 개가 읽기 쉽다.
 * 권한도 그만큼만 준다 (차트의 Role 참고).
 */
@Component
class KubernetesScaleClient(
    private val objectMapper: ObjectMapper,
    properties: ExecutorScalingProperties,
) : ExecutorScaleClient {

    private val log = LoggerFactory.getLogger(javaClass)

    private val token: String? = readIfExists(TOKEN_PATH)
    // 실행기는 별도 네임스페이스에 있을 수 있다. 설정이 있으면 그쪽을, 없으면 내 것을 쓴다.
    private val namespace: String? =
        properties.namespace.takeIf { it.isNotBlank() } ?: readIfExists(NAMESPACE_PATH)
    private val host: String? = System.getenv("KUBERNETES_SERVICE_HOST")

    override val available: Boolean = token != null && namespace != null && host != null

    override val unavailableReason: String? =
        if (available) null else "클러스터 밖에서 실행 중이라 실행기 replica 를 조정할 수 없습니다."

    private val restClient: RestClient by lazy {
        RestClient.builder()
            .baseUrl("https://$host:${System.getenv("KUBERNETES_SERVICE_PORT") ?: "443"}")
            .defaultHeader("Authorization", "Bearer $token")
            .build()
    }

    override fun read(deployment: String): Pair<Int, Int> {
        val body = restClient.get()
            .uri("/apis/apps/v1/namespaces/{ns}/deployments/{name}", namespace, deployment)
            .retrieve()
            .body(String::class.java)
            ?: return 0 to 0

        val root = objectMapper.readTree(body)
        val desired = root.path("spec").path("replicas").asInt(0)
        // 아직 아무 파드도 준비되지 않았으면 status.readyReplicas 자체가 없다.
        val ready = root.path("status").path("readyReplicas").asInt(0)
        return desired to ready
    }

    override fun scale(deployment: String, replicas: Int) {
        restClient.patch()
            .uri("/apis/apps/v1/namespaces/{ns}/deployments/{name}/scale", namespace, deployment)
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .body("""{"spec":{"replicas":$replicas}}""")
            .retrieve()
            .toBodilessEntity()

        log.info("실행기 replica 를 {} 로 조정했습니다: {}", replicas, deployment)
    }

    private fun readIfExists(path: String): String? = runCatching {
        Path.of(path).takeIf { Files.isReadable(it) }?.let { Files.readString(it).trim() }
    }.getOrNull()

    private companion object {
        const val TOKEN_PATH = "/var/run/secrets/kubernetes.io/serviceaccount/token"
        const val NAMESPACE_PATH = "/var/run/secrets/kubernetes.io/serviceaccount/namespace"
    }
}
