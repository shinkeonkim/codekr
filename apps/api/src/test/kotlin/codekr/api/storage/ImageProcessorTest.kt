package codekr.api.storage

import codekr.api.common.error.ApiException
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** 올린 이미지의 검증과 변환 (#115). */
class ImageProcessorTest {

    private val processor = ImageProcessor()

    @Test
    fun `이미지가 아닌 파일은 거부한다`() {
        // 확장자를 png 로 바꾼 실행 파일이 여기서 걸려야 한다.
        val notAnImage = "MZ ".toByteArray() + ByteArray(1024)

        val error = assertFailsWith<ApiException> { processor.toSquarePng(notAnImage) }
        assertTrue(error.message!!.contains("이미지"))
    }

    @Test
    fun `상한을 넘는 파일은 디코딩하기 전에 거부한다`() {
        // 거대한 이미지는 디코딩 자체가 공격이 된다.
        val huge = ByteArray(ImageProcessor.MAX_UPLOAD_BYTES + 1)

        assertFailsWith<ApiException> { processor.toSquarePng(huge) }
    }

    @Test
    fun `어떤 크기를 넣어도 같은 크기로 나온다`() {
        // 원본을 그대로 두면 목록에서 수 MB 를 내려받게 된다.
        val processed = processor.toSquarePng(pngOf(width = 1200, height = 400))

        val decoded = ImageIO.read(processed.bytes.inputStream())
        assertEquals(ImageProcessor.SIZE, decoded.width)
        assertEquals(ImageProcessor.SIZE, decoded.height)
    }

    @Test
    fun `png 가 아닌 형식도 png 로 다시 만든다`() {
        val processed = processor.toSquarePng(imageOf("jpg", 500, 500))

        assertEquals(ImageProcessor.CONTENT_TYPE, processed.contentType)
        // PNG 서명으로 시작해야 한다 — 다시 인코딩됐다는 뜻이다.
        val signature = listOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte())
        assertEquals(signature, processed.bytes.take(4))
    }

    @Test
    fun `키에 원본 파일명을 쓰지 않는다`() {
        val bytes = pngOf(64, 64)

        val key = processor.keyFor("avatars", bytes)

        // 경로 조작·충돌·파일명에 담긴 개인정보가 한꺼번에 사라진다.
        assertTrue(key.startsWith("avatars/"))
        assertTrue(key.endsWith(".png"))
        assertEquals(1, key.count { it == '/' }, "키에 경로 구분자가 더 있으면 안 됩니다: $key")
    }

    @Test
    fun `같은 이미지는 같은 키가 된다`() {
        val bytes = pngOf(64, 64)

        // URL 이 내용에 묶이므로 오래 캐시해도 안전하다.
        assertEquals(processor.keyFor("avatars", bytes), processor.keyFor("avatars", bytes))
        assertNotEquals(processor.keyFor("avatars", bytes), processor.keyFor("avatars", pngOf(65, 65)))
    }

    private fun pngOf(width: Int, height: Int): ByteArray = imageOf("png", width, height)

    private fun imageOf(format: String, width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply {
            color = Color(width % 256, height % 256, 128)
            fillRect(0, 0, width, height)
            dispose()
        }
        val output = ByteArrayOutputStream()
        ImageIO.write(image, format, output)
        return output.toByteArray()
    }
}
