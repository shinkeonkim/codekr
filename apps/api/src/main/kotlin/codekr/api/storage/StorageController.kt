package codekr.api.storage

import codekr.api.config.security.PublicApi
import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

/**
 * 저장된 이미지를 내보낸다 (#115).
 *
 * 버킷을 공개로 열지 않고 API 를 거치는 이유:
 *   - 버킷 정책을 운영과 로컬에서 따로 맞출 필요가 없다. **같은 경로로 돈다**
 *   - 나중에 접근 제어가 필요해져도 여기만 고치면 된다
 *
 * 키에 **내용 해시**가 들어 있어 URL 이 내용에 묶인다. 그래서 오래 캐시해도 안전하다 —
 * 이미지가 바뀌면 주소가 바뀐다.
 */
@RestController
@RequestMapping("/api/v1/files")
class StorageController(private val storage: ObjectStorage) {

    @PublicApi
    @GetMapping("/{prefix}/{name}")
    fun get(@PathVariable prefix: String, @PathVariable name: String): ResponseEntity<ByteArray> {
        // 경로 조각을 그대로 키로 쓰므로 조각에 구분자가 섞이면 안 된다.
        if (!SAFE_SEGMENT.matches(prefix) || !SAFE_SEGMENT.matches(name)) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "올바르지 않은 경로입니다.")
        }
        val stored = storage.get("$prefix/$name") ?: throw ApiException(ErrorCode.FILE_NOT_FOUND)

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(stored.contentType))
            .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
            .body(stored.bytes)
    }

    private companion object {
        val SAFE_SEGMENT = Regex("^[a-zA-Z0-9._-]{1,80}$")
    }
}
