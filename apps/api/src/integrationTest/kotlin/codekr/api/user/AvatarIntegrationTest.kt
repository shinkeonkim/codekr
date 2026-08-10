package codekr.api.user

import codekr.api.auth.security.JwtTokenProvider
import codekr.api.storage.ObjectStorage
import codekr.api.storage.StoredObject
import codekr.api.support.IntegrationTestBase
import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import codekr.api.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 프로필 아바타 (#116).
 *
 * 저장소는 메모리 구현으로 바꿔 끼운다 — 이 시험이 보려는 것은 **정책**이지 S3 가 아니다.
 * 실제 저장소 동작은 `S3ObjectStorageLiveTest` 가 본다 (#115).
 */
@Import(AvatarIntegrationTest.MemoryStorageConfig::class)
class AvatarIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var storage: ObjectStorage

    private lateinit var token: String

    @BeforeEach
    fun setUp() {
        (storage as MemoryStorage).clear()
        val user = userRepository.save(User("me@codekr.dev", "x", "풀이왕", setOf(UserRole.USER)))
        token = tokenProvider.issueAccessToken(user)
    }

    @Test
    fun `올리면 프로필에 주소가 붙는다`() {
        val url = upload(pngOf(400, 300))

        assertTrue(url.startsWith("/api/v1/files/avatars/"), "주소가 다릅니다: $url")
        mockMvc.perform(get("/api/v1/users/풀이왕").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.avatarUrl").value(url))
    }

    @Test
    fun `이미지가 아니면 거부한다`() {
        // 확장자와 Content-Type 을 믿지 않는다 (#115).
        mockMvc.perform(
            multipart(HttpMethod.PUT, "/api/v1/users/me/avatar")
                .file(MockMultipartFile("file", "evil.png", MediaType.IMAGE_PNG_VALUE, ByteArray(64) { 0x4D }))
                .header("Authorization", "Bearer $token")
                .with { it.method = "PUT"; it },
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `바꾸면 옛 이미지를 지운다`() {
        val first = upload(pngOf(100, 100))
        val second = upload(pngOf(200, 200))

        // 참조가 끊긴 파일이 쌓이지 않아야 한다 (#115).
        assertEquals(1, (storage as MemoryStorage).size(), "옛 이미지가 남았습니다")
        assertTrue(first != second)
    }

    @Test
    fun `같은 이미지를 다시 올려도 사라지지 않는다`() {
        val bytes = pngOf(120, 120)
        val first = upload(bytes)
        val second = upload(bytes)

        // 키가 같으므로 방금 올린 것을 지우면 안 된다.
        assertEquals(first, second)
        assertEquals(1, (storage as MemoryStorage).size())
        mockMvc.perform(get("/api/v1/users/풀이왕").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.avatarUrl").value(first))
    }

    @Test
    fun `지우면 프로필에서도 저장소에서도 사라진다`() {
        upload(pngOf(100, 100))

        mockMvc.perform(delete("/api/v1/users/me/avatar").header("Authorization", "Bearer $token"))
            .andExpect(status().isNoContent)

        assertEquals(0, (storage as MemoryStorage).size())
        mockMvc.perform(get("/api/v1/users/풀이왕").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.avatarUrl").doesNotExist())
    }

    @Test
    fun `아바타가 없어도 지우기는 오류가 아니다`() {
        // 여러 번 눌러도 같아야 한다.
        mockMvc.perform(delete("/api/v1/users/me/avatar").header("Authorization", "Bearer $token"))
            .andExpect(status().isNoContent)
    }

    private fun upload(bytes: ByteArray): String {
        val response = mockMvc.perform(
            multipart(HttpMethod.PUT, "/api/v1/users/me/avatar")
                .file(MockMultipartFile("file", "a.png", MediaType.IMAGE_PNG_VALUE, bytes))
                .header("Authorization", "Bearer $token")
                .with { it.method = "PUT"; it },
        ).andExpect(status().isOk).andReturn().response.contentAsString

        return Regex("\"avatarUrl\":\"([^\"]+)\"").find(response)!!.groupValues[1]
    }

    private fun pngOf(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        image.setRGB(0, 0, width * height)
        val output = ByteArrayOutputStream()
        ImageIO.write(image, "png", output)
        return output.toByteArray()
    }

    @TestConfiguration
    class MemoryStorageConfig {
        @Bean
        @Primary
        fun memoryStorage(): ObjectStorage = MemoryStorage()
    }

    class MemoryStorage : ObjectStorage {
        private val objects = ConcurrentHashMap<String, StoredObject>()

        override val available = true

        override fun put(key: String, bytes: ByteArray, contentType: String) {
            objects[key] = StoredObject(bytes, contentType)
        }

        override fun get(key: String): StoredObject? = objects[key]

        override fun delete(key: String) {
            objects.remove(key)
        }

        fun size(): Int = objects.size

        fun clear() = objects.clear()
    }
}
