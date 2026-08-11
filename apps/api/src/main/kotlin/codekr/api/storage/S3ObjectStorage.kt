package codekr.api.storage

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
 * 설정이 없으면 [available] 이 false 이고 아무것도 하지 않는다. 스토리지 없이도
 * 나머지 기능이 돌아야 한다 — 아바타 때문에 서비스 전체가 뜨지 못하면 안 된다.
 */
@Component
class S3ObjectStorage(private val properties: StorageProperties) : ObjectStorage {

    private val log = LoggerFactory.getLogger(javaClass)

    override val available: Boolean = properties.endpoint.isNotBlank()

    private val client: S3Client? by lazy {
        if (!available) return@lazy null
        runCatching { buildClient() }
            .onFailure { log.error("오브젝트 스토리지 연결 실패: {}", it.message) }
            .getOrNull()
    }

    override fun put(key: String, bytes: ByteArray, contentType: String) {
        val s3 = client ?: return
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
