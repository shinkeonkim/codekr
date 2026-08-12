package codekr.api.scaling.service

import codekr.api.config.properties.ExecutorScalingProperties
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.ObjectMapper
import java.net.http.HttpClient
import java.time.Duration

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

    private val credentials: ServiceAccountCredentials? = ServiceAccountCredentials.load(properties.namespace)

    override val available: Boolean = credentials != null

    override val namespace: String? = credentials?.namespace

    override val unavailableReason: String? =
        if (available) null else "클러스터 밖에서 실행 중이라 실행기 replica 를 조정할 수 없습니다."

    /**
     * API 서버 인증서를 **클러스터 CA 로** 검증한다 (#237).
     *
     * 이것이 없으면 JVM 기본 신뢰 저장소를 쓰는데, 거기에는 클러스터 CA 가 없어서 TLS
     * 악수 단계에서 실패한다. 홈랩 배포에서 실제로 그랬다 — 토큰도 권한도 멀쩡했다.
     */
    private val restClient: RestClient by lazy {
        val session = requireNotNull(credentials) { "클러스터 밖에서는 호출하지 않는다" }
        val httpClient = HttpClient.newBuilder()
            .sslContext(session.sslContext)
            .connectTimeout(CONNECT_TIMEOUT)
            .build()
        // **기다림에도 끝이 있어야 한다** (#237). 시간 제한이 없으면 API 서버가 응답하지
        // 않을 때 어드민 화면이 아무것도 못 그린 채 멈춘다 — 고장인지 느린지도 구분되지 않는다.
        val requestFactory = JdkClientHttpRequestFactory(httpClient).apply { setReadTimeout(READ_TIMEOUT) }
        RestClient.builder()
            .requestFactory(requestFactory)
            .baseUrl(session.baseUrl)
            .defaultHeader("Authorization", "Bearer ${session.token}")
            .build()
    }

    override fun read(deployment: String): Pair<Int, Int> {
        val body = call("읽기", deployment) {
            restClient.get()
                .uri("/apis/apps/v1/namespaces/{ns}/deployments/{name}", namespace, deployment)
                .retrieve()
                .body(String::class.java)
        } ?: return 0 to 0

        val root = objectMapper.readTree(body)
        val desired = root.path("spec").path("replicas").asInt(0)
        // 아직 아무 파드도 준비되지 않았으면 status.readyReplicas 자체가 없다.
        val ready = root.path("status").path("readyReplicas").asInt(0)
        return desired to ready
    }

    override fun scale(deployment: String, replicas: Int) {
        call("조정", deployment) {
            restClient.patch()
                .uri("/apis/apps/v1/namespaces/{ns}/deployments/{name}/scale", namespace, deployment)
                .contentType(MediaType.valueOf("application/merge-patch+json"))
                .body("""{"spec":{"replicas":$replicas}}""")
                .retrieve()
                .toBodilessEntity()
        }

        log.info("실행기 replica 를 {} 로 조정했습니다: {}/{}", replicas, namespace, deployment)
    }

    /**
     * 실패를 **분류해서** 올린다 (#237).
     *
     * 상태 코드와 응답 본문은 여기서 로그에 남긴다. 부르는 쪽까지 올라가면 화면에 닿을
     * 위험이 생기고, 여기가 유일하게 그것을 아는 자리다.
     */
    private fun <T> call(action: String, deployment: String, request: () -> T): T = try {
        request()
    } catch (error: RestClientResponseException) {
        val failure = when (error.statusCode.value()) {
            401, 403 -> ScaleAccessFailure.FORBIDDEN
            404 -> ScaleAccessFailure.NOT_FOUND
            else -> ScaleAccessFailure.UNKNOWN
        }
        val detail = "HTTP ${error.statusCode.value()} ${error.responseBodyAsString.take(BODY_LOG_LIMIT)}"
        log.error("실행기 배포 {} 실패: {}/{} — {}", action, namespace, deployment, detail)
        throw ScaleAccessException(failure, detail, error)
    } catch (error: ResourceAccessException) {
        // 연결 자체가 되지 않았다. TLS 신뢰 실패도 여기로 온다 — 원인 사슬까지 남긴다.
        log.error("실행기 배포 {} 실패 (연결): {}/{}", action, namespace, deployment, error)
        throw ScaleAccessException(ScaleAccessFailure.UNREACHABLE, error.message ?: "연결 실패", error)
    }

    private companion object {
        /** 응답 본문은 길 수 있다. 원인을 아는 데 필요한 앞부분만 남긴다. */
        const val BODY_LOG_LIMIT = 500

        /** 같은 클러스터 안의 API 서버다. 붙는 데 3초가 걸린다면 이미 정상이 아니다. */
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(3)

        /** Deployment 하나를 읽는 호출이다. 5초를 넘길 이유가 없다. */
        val READ_TIMEOUT: Duration = Duration.ofSeconds(5)
    }
}
