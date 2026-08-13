package codekr.api.storage

import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 붙지 못하는 저장소가 **쓰기를 조용히 삼키지 않는지** 본다 (#424).
 *
 * 운영에서 자격증명이 안 닿아 클라이언트를 못 만들었는데 `available` 이 true 였고,
 * `put` 이 아무 일도 안 하고 정상 반환했다. 그래서 올린 적 없는 파일을 가리키는
 * `avatar_key` 가 DB 에 남았다.
 *
 * 실제 S3 에 붙는 시험은 `S3ObjectStorageLiveTest` 에 따로 있다. 여기서는 **붙지 못하는
 * 경우의 약속**만 고정한다 — 그것이 이 이슈에서 깨진 부분이다.
 */
class S3ObjectStorageAvailabilityTest {

    @Test
    @DisplayName("설정이 없으면 쓸 수 없다고 답한다")
    fun unavailableWithoutEndpoint() {
        val storage = S3ObjectStorage(StorageProperties())

        assertThat(storage.available).isFalse()
    }

    @Test
    @DisplayName("주소는 있는데 자격증명이 비면 쓸 수 없다 — 주소만 보고 판단하지 않는다")
    fun unavailableWhenCredentialsMissing() {
        val storage = S3ObjectStorage(StorageProperties(endpoint = "http://localhost:9000"))

        // 전에는 endpoint 만 봐서 여기가 true 였다. 그것이 #424 의 시작점이다.
        assertThat(storage.available).isFalse()
    }

    @Test
    @DisplayName("저장하지 못하면 예외를 낸다 — 조용히 성공하지 않는다")
    fun putFailsLoudlyWhenUnavailable() {
        val storage = S3ObjectStorage(StorageProperties(endpoint = "http://localhost:9000"))

        assertThatThrownBy { storage.put("avatars/x.png", byteArrayOf(1), "image/png") }
            .isInstanceOf(ApiException::class.java)
            .extracting { (it as ApiException).errorCode }
            .isEqualTo(ErrorCode.STORAGE_UNAVAILABLE)
    }

    @Test
    @DisplayName("읽기는 조용히 없는 것으로 답한다 — 쓰기와 다르게 다룬다")
    fun getStaysQuiet() {
        val storage = S3ObjectStorage(StorageProperties(endpoint = "http://localhost:9000"))

        assertThat(storage.get("avatars/x.png")).isNull()
    }

    @Test
    @DisplayName("지우기도 조용하다 — 여러 번 불려도 같아야 한다")
    fun deleteStaysQuiet() {
        val storage = S3ObjectStorage(StorageProperties(endpoint = "http://localhost:9000"))

        storage.delete("avatars/x.png")
    }
}
