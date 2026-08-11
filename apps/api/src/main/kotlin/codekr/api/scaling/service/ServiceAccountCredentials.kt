package codekr.api.scaling.service

import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

/**
 * 파드에 주입된 ServiceAccount 자격증명 (#40).
 *
 * **인증서까지 읽는다** (#237). 토큰만 읽고 TLS 는 JVM 기본 신뢰 저장소에 맡기면 반드시
 * 실패한다 — 쿠버네티스 API 서버의 인증서는 클러스터가 스스로 발급한 것이라 공인 CA 목록에
 * 없다. 실제로 홈랩 배포에서 그것 때문에 실행기 상태를 읽지 못했다.
 *
 * `available` 이 false 인 것은 **고장이 아니라 설정**이다. 로컬 docker compose 에서는
 * 이 파일들이 없는 것이 정상이다.
 */
class ServiceAccountCredentials(
    val token: String,
    val namespace: String,
    val host: String,
    val port: String,
    /** API 서버 인증서를 검증할 신뢰 관계. 클러스터 CA 하나만 담는다. */
    val sslContext: SSLContext,
) {
    val baseUrl: String get() = "https://$host:$port"

    companion object {
        private const val DIRECTORY = "/var/run/secrets/kubernetes.io/serviceaccount"

        /**
         * 파드 안이면 자격증명을, 아니면 null 을 돌려준다.
         *
         * 하나라도 없으면 전부 없는 것으로 친다 — 반쪽짜리 상태로 호출을 시도해 봐야
         * 무엇이 없어서 실패했는지만 흐려진다.
         */
        fun load(namespaceOverride: String): ServiceAccountCredentials? {
            val token = readIfExists("$DIRECTORY/token") ?: return null
            val namespace = namespaceOverride.takeIf { it.isNotBlank() }
                ?: readIfExists("$DIRECTORY/namespace")
                ?: return null
            val host = System.getenv("KUBERNETES_SERVICE_HOST") ?: return null
            val authority = readIfExists("$DIRECTORY/ca.crt") ?: return null

            return ServiceAccountCredentials(
                token = token,
                namespace = namespace,
                host = host,
                port = System.getenv("KUBERNETES_SERVICE_PORT") ?: "443",
                sslContext = sslContextTrusting(authority),
            )
        }

        /**
         * 이 인증서 하나만 믿는 TLS 문맥을 만든다.
         *
         * JVM 기본 신뢰 저장소를 함께 쓰지 않는다. 우리가 부르는 곳은 클러스터 API 서버
         * 하나뿐이고, 그 인증서는 이 CA 가 발급한 것이다 — 다른 것을 믿을 이유가 없다.
         */
        internal fun sslContextTrusting(certificatePem: String): SSLContext {
            val certificates = CertificateFactory.getInstance("X.509")
                .generateCertificates(certificatePem.byteInputStream())

            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
            keyStore.load(null, null)
            certificates.forEachIndexed { index, certificate ->
                keyStore.setCertificateEntry("kubernetes-$index", certificate)
            }

            val trustManagers = TrustManagerFactory
                .getInstance(TrustManagerFactory.getDefaultAlgorithm())
                .apply { init(keyStore) }

            return SSLContext.getInstance("TLS").apply {
                init(null, trustManagers.trustManagers, null)
            }
        }

        private fun readIfExists(path: String): String? = runCatching {
            Path.of(path).takeIf { Files.isReadable(it) }?.let { Files.readString(it).trim() }
        }.getOrNull()
    }
}
