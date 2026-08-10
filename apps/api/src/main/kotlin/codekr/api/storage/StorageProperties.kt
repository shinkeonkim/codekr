package codekr.api.storage

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 오브젝트 스토리지 설정 (#115).
 *
 * **운영도 로컬도 같은 S3 호환 API 를 쓴다.** 로컬에서만 다른 저장 방식을 쓰면
 * 로컬에서 되던 것이 운영에서 안 되는 일이 생기고, 그때 원인을 찾기 어렵다.
 */
@ConfigurationProperties(prefix = "codekr.storage")
data class StorageProperties(
    /** 비어 있으면 스토리지를 쓰지 않는다. 업로드 API 가 503 을 낸다. */
    val endpoint: String = "",
    val bucket: String = "codekr",
    val accessKey: String = "",
    val secretKey: String = "",
    /**
     * MinIO 는 가상 호스트 스타일 주소를 쓰지 않는다 (`bucket.host` 형태).
     * 경로 스타일(`host/bucket`)로 강제해야 로컬에서 붙는다.
     */
    val pathStyle: Boolean = true,
    val region: String = "us-east-1",
)
