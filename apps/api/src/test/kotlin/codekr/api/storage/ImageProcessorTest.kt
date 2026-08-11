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

        val error = assertFailsWith<ApiException> { processor.process(notAnImage, ImagePolicy.AVATAR) }
        assertTrue(error.message!!.contains("이미지"))
    }

    @Test
    fun `상한을 넘는 파일은 디코딩하기 전에 거부한다`() {
        // 거대한 이미지는 디코딩 자체가 공격이 된다.
        val huge = ByteArray(ImageLimits.MAX_UPLOAD_BYTES + 1)

        assertFailsWith<ApiException> { processor.process(huge, ImagePolicy.AVATAR) }
    }

    @Test
    fun `어떤 크기를 넣어도 같은 크기로 나온다`() {
        // 원본을 그대로 두면 목록에서 수 MB 를 내려받게 된다.
        val processed = processor.process(pngOf(width = 1200, height = 400), ImagePolicy.AVATAR)

        val decoded = ImageIO.read(processed.bytes.inputStream())
        assertEquals(ImagePolicy.AVATAR.maxEdge, decoded.width)
        assertEquals(ImagePolicy.AVATAR.maxEdge, decoded.height)
    }

    @Test
    fun `png 가 아닌 형식도 png 로 다시 만든다`() {
        val processed = processor.process(imageOf("jpg", 500, 500), ImagePolicy.AVATAR)

        assertEquals(ImagePolicy.AVATAR.contentType, processed.contentType)
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

    @Test
    fun `압축 폭탄은 디코딩하기 전에 거부한다`() {
        // 거대한 크기를 **주장하는** PNG 다. 파일은 수십 바이트지만 디코딩하면
        // 픽셀당 4바이트로 수 GB 가 된다. **용량 상한만으로는 못 막는다.**
        //
        // 시험이 스스로 그 이미지를 만들면 시험 JVM 이 먼저 죽는다 — 그래서 헤더만 만든다.
        val bomb = pngHeaderClaiming(width = 50_000, height = 50_000)
        assertTrue(bomb.size < ImageLimits.MAX_UPLOAD_BYTES, "폭탄이 용량 상한에는 걸리지 않아야 시험이 뜻을 가진다")

        val error = assertFailsWith<ApiException> { processor.process(bomb, ImagePolicy.AVATAR) }
        assertEquals(codekr.api.common.error.ErrorCode.IMAGE_TOO_LARGE, error.errorCode)
    }

    @Test
    fun `첨부는 비율을 유지한 채 최대 변에 맞춘다`() {
        val processed = processor.process(pngOf(width = 4000, height = 2000), ImagePolicy.ATTACHMENT)
        val decoded = ImageIO.read(processed.bytes.inputStream())

        assertEquals(ImagePolicy.ATTACHMENT.maxEdge, decoded.width)
        assertEquals(ImagePolicy.ATTACHMENT.maxEdge / 2, decoded.height)
        assertEquals("image/jpeg", processed.contentType)
    }

    @Test
    fun `작은 이미지를 키우지 않는다`() {
        // 늘리면 흐려지기만 하고 용량은 늘어난다.
        val processed = processor.process(pngOf(width = 300, height = 200), ImagePolicy.ATTACHMENT)
        val decoded = ImageIO.read(processed.bytes.inputStream())

        assertEquals(300, decoded.width)
        assertEquals(200, decoded.height)
    }

    @Test
    fun `첨부는 압축으로 원본보다 작아진다`() {
        // 압축으로 용량을 제어한다는 것이 이 규격의 목적이다.
        // **단색이 아니라 잡음으로 만든다** — 단색 PNG 는 원래 수 KB 라 무엇을 줄여도
        // 줄지 않는다. 실제로 올라오는 것은 사진이다.
        // 잡음 PNG 는 거의 압축되지 않아 크다 — 상한(5MB) 안에 들어오는 크기로 잡는다.
        val original = noisyPngOf(width = 1200, height = 800)
        val processed = processor.process(original, ImagePolicy.ATTACHMENT)

        assertTrue(
            processed.bytes.size < original.size,
            "원본 ${original.size}B → 결과 ${processed.bytes.size}B",
        )
    }

    @Test
    fun `저장 키의 확장자가 규격을 따른다`() {
        assertTrue(processor.keyFor("avatars", ByteArray(4), ImagePolicy.AVATAR).endsWith(".png"))
        assertTrue(processor.keyFor("posts", ByteArray(4), ImagePolicy.ATTACHMENT).endsWith(".jpg"))
    }

    /**
     * 크기만 주장하는 PNG. 픽셀 데이터는 없다.
     *
     * IHDR 만 있으면 리더가 가로·세로를 읽는다 — 우리가 디코딩 전에 보는 것이 그것이다.
     */
    private fun pngHeaderClaiming(width: Int, height: Int): ByteArray {
        val header = ByteArrayOutputStream()
        header.write(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)) // PNG 서명
        val ihdr = ByteArrayOutputStream().apply {
            write("IHDR".toByteArray())
            writeInt(width)
            writeInt(height)
            write(byteArrayOf(8, 2, 0, 0, 0)) // 8비트 트루컬러
        }.toByteArray()
        header.writeInt(ihdr.size - 4)
        header.write(ihdr)
        header.writeInt(java.util.zip.CRC32().apply { update(ihdr) }.value.toInt())
        return header.toByteArray()
    }

    private fun ByteArrayOutputStream.writeInt(value: Int) {
        write(byteArrayOf((value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte()))
    }

    /** 사진처럼 압축이 잘 되지 않는 이미지. 결정적이라 시험이 흔들리지 않는다. */
    private fun noisyPngOf(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        var seed = 12345
        for (y in 0 until height) {
            for (x in 0 until width) {
                seed = seed * 1103515245 + 12345
                image.setRGB(x, y, seed ushr 8)
            }
        }
        val output = ByteArrayOutputStream()
        ImageIO.write(image, "png", output)
        return output.toByteArray()
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
