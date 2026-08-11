package codekr.api.storage

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 실제 오브젝트 스토리지에 붙는 시험 (#115).
 *
 * MinIO 가 떠 있어야 한다 (`make infra-up`). 없으면 건너뛴다 — 스토리지가 없는 곳에서
 * 전체 시험이 깨지면 안 된다.
 */
@EnabledIfEnvironmentVariable(named = "CODEKR_STORAGE_TEST", matches = "1")
class S3ObjectStorageLiveTest {

    private val storage = S3ObjectStorage(
        StorageProperties(
            endpoint = System.getenv("CODEKR_STORAGE_ENDPOINT") ?: "http://localhost:19000",
            bucket = "codekr-test",
            accessKey = System.getenv("CODEKR_STORAGE_ACCESS_KEY") ?: "codekr",
            secretKey = System.getenv("CODEKR_STORAGE_SECRET_KEY") ?: "codekr_local_pw",
        ),
    )

    @Test
    fun `올리고 받고 지운다`() {
        val processor = ImageProcessor()
        val image = processor.toSquarePng(samplePng())
        val key = processor.keyFor("avatars", image.bytes)

        storage.put(key, image.bytes, image.contentType)

        val fetched = storage.get(key)
        assertNotNull(fetched)
        assertEquals(image.contentType, fetched.contentType)
        assertEquals(image.bytes.size, fetched.bytes.size)

        storage.delete(key)
        assertNull(storage.get(key), "지운 뒤에는 없어야 합니다")
    }

    @Test
    fun `없는 키를 지우는 것은 오류가 아니다`() {
        // 여러 번 불려도 같아야 한다.
        storage.delete("avatars/does-not-exist.png")
        storage.delete("avatars/does-not-exist.png")
    }

    private fun samplePng(): ByteArray {
        val image = BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB)
        val output = ByteArrayOutputStream()
        ImageIO.write(image, "png", output)
        return output.toByteArray()
    }
}
