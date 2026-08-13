package codekr.api.storage

import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchBucketException
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import java.net.URI

/**
 * S3 호환 저장소 (#115). 운영은 S3, 로컬은 MinIO — 같은 코드다.
 *
 * 설정이 없거나 붙지 못하면 [available] 이 false 다. 스토리지 없이도 나머지 기능이
 * 돌아야 한다 — 아바타 때문에 서비스 전체가 뜨지 못하면 안 된다.
 *
 * **다만 읽기와 쓰기를 다르게 다룬다** (#424). 읽기는 조용히 없는 것으로 답하고,
 * **쓰기는 실패로 알린다.** 저장하지 못했는데 성공했다고 답하면 부르는 쪽이 키를
 * 기록하고, 그때부터 DB 가 없는 파일을 가리킨다.
 */
@Component
class S3ObjectStorage(private val properties: StorageProperties) : ObjectStorage {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 쓸 수 있는 상태인가.
     *
     * **주소가 있는 것과 붙을 수 있는 것은 다르다** (#424). 전에는 endpoint 만 봐서,
     * 자격증명이 비어 클라이언트를 못 만들어도 `true` 였다. 그러면 위층은 저장할 수
     * 있다고 믿고 [put] 을 부르는데, 거기서 조용히 아무 일도 일어나지 않았다 —
     * **없는 파일을 가리키는 행이 DB 에 남았다.**
     */
    override val available: Boolean get() = client != null

    private val configured: Boolean = properties.endpoint.isNotBlank()

    private val client: S3Client? by lazy {
        if (!configured) return@lazy null
        runCatching { buildClient() }
            .onFailure { log.error("오브젝트 스토리지 연결 실패: {}", it.message) }
            .getOrNull()
    }

    /**
     * 저장한다.
     *
     * **저장하지 못하면 실패로 알린다** (#424). 조용히 넘어가면 부르는 쪽은 성공한 줄
     * 알고 키를 기록한다. 스토리지가 없어도 서비스가 떠야 한다는 규칙(#115)은
     * **읽기에 대한 것**이지, 쓰기가 거짓말을 해도 된다는 뜻이 아니다.
     */
    override fun put(key: String, bytes: ByteArray, contentType: String) {
        val s3 = client ?: throw ApiException(ErrorCode.STORAGE_UNAVAILABLE)
        ensureBucket(s3)
        s3.putObject(
            PutObjectRequest.builder()
                .bucket(properties.bucket)
                .key(key)
                .contentType(contentType)
                .build(),
            RequestBody.fromBytes(bytes),
        )
    }

    override fun get(key: String): StoredObject? {
        val s3 = client ?: return null
        return try {
            val response = s3.getObjectAsBytes(
                GetObjectRequest.builder().bucket(properties.bucket).key(key).build(),
            )
            StoredObject(response.asByteArray(), response.response().contentType() ?: "application/octet-stream")
        } catch (e: NoSuchKeyException) {
            log.debug("없는 객체를 요청했습니다: {}", key, e)
            null
        } catch (e: NoSuchBucketException) {
            log.warn("버킷이 없습니다: {}", properties.bucket, e)
            null
        }
    }

    /**
     * 없는 키를 지우는 것은 오류가 아니다.
     *
     * 없는 **버킷**도 마찬가지다 — 아직 아무것도 올리지 않은 환경에서 정리를 돌리면
     * 버킷이 없을 수 있고, 그때 예외가 나면 지우려던 쪽이 실패한다.
     */
    override fun delete(key: String) {
        val s3 = client ?: return
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(properties.bucket).key(key).build())
        } catch (e: NoSuchBucketException) {
            log.debug("버킷이 아직 없습니다: {}", properties.bucket, e)
        } catch (e: NoSuchKeyException) {
            log.debug("이미 없는 객체입니다: {}", key, e)
        }
    }

    /**
     * 버킷이 없으면 만든다.
     *
     * 로컬에서 `make up` 만으로 돌아야 하는데, MinIO 는 버킷을 자동으로 만들지 않는다.
     * 운영에서는 이미 있으므로 아무 일도 하지 않는다.
     */
    private fun ensureBucket(s3: S3Client) {
        runCatching { s3.headBucket { it.bucket(properties.bucket) } }
            .onFailure {
                if (it is S3Exception || it is NoSuchBucketException) {
                    runCatching {
                        s3.createBucket(CreateBucketRequest.builder().bucket(properties.bucket).build())
                    }
                }
            }
    }

    private fun buildClient(): S3Client = S3Client.builder()
        .endpointOverride(URI.create(properties.endpoint))
        .region(Region.of(properties.region))
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey, properties.secretKey),
            ),
        )
        // MinIO 는 가상 호스트 스타일 주소를 쓰지 않는다.
        .forcePathStyle(properties.pathStyle)
        .build()
}
