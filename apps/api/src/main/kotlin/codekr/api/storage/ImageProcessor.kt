package codekr.api.storage

import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import org.springframework.stereotype.Component
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.HexFormat
import javax.imageio.ImageIO
import kotlin.math.min

/**
 * 올린 이미지를 안전한 형태로 다시 만든다 (#115).
 *
 * **확장자를 믿지 않는다.** 디코딩에 성공해야 이미지다 — `.png` 로 이름만 바꾼
 * 실행 파일은 여기서 걸린다.
 *
 * 다시 인코딩하는 것이 핵심이다. 그 결과로 세 가지가 한꺼번에 해결된다.
 *   1. 내용 기반 검증 (디코딩 실패 = 이미지 아님)
 *   2. 출력 크기가 입력과 무관하게 정해진다 — 목록에서 수 MB 를 내려받는 일이 없다
 *   3. EXIF 와 그 안에 숨은 것이 사라진다. 사진의 촬영 위치가 아바타로 새지 않는다
 */
@Component
class ImageProcessor {

    /**
     * 정사각형으로 잘라 [SIZE] 픽셀 PNG 로 다시 만든다.
     *
     * 가운데를 기준으로 자른다 — 얼굴 사진이 대부분이고, 가운데가 잘려 나가는 경우는 드물다.
     */
    fun toSquarePng(input: ByteArray): ProcessedImage {
        if (input.size > MAX_UPLOAD_BYTES) {
            throw ApiException(
                ErrorCode.VALIDATION_ERROR,
                "이미지는 ${MAX_UPLOAD_BYTES / 1024 / 1024}MB 이하여야 합니다.",
            )
        }

        val source = runCatching { ImageIO.read(ByteArrayInputStream(input)) }.getOrNull()
            ?: throw ApiException(ErrorCode.VALIDATION_ERROR, "이미지 파일이 아닙니다.")

        val side = min(source.width, source.height)
        val cropped = source.getSubimage((source.width - side) / 2, (source.height - side) / 2, side, side)

        // TYPE_INT_RGB 는 투명도를 버린다. 아바타는 원형으로 잘라 보여주므로
        // 투명한 배경이 검게 나오면 안 된다 — ARGB 를 유지한다.
        val resized = BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB)
        resized.createGraphics().apply {
            drawImage(cropped.getScaledInstance(SIZE, SIZE, java.awt.Image.SCALE_SMOOTH), 0, 0, null)
            dispose()
        }

        val output = ByteArrayOutputStream()
        ImageIO.write(resized, "png", output)
        return ProcessedImage(output.toByteArray(), CONTENT_TYPE)
    }

    /**
     * 저장 키.
     *
     * **원본 파일명을 쓰지 않는다.** 경로 조작(`../`), 충돌, 그리고 파일명에 담긴
     * 개인정보(이름·날짜)가 그대로 남는 문제가 한꺼번에 생긴다.
     *
     * 내용 해시를 쓰므로 같은 이미지는 같은 키가 되고, **URL 이 내용에 묶인다** —
     * 바뀌지 않는 주소라 오래 캐시해도 안전하다.
     */
    fun keyFor(prefix: String, bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return "$prefix/${HexFormat.of().formatHex(digest).take(HASH_LENGTH)}.png"
    }

    companion object {
        /** 아바타 한 변의 픽셀. 목록에서도 쓰이므로 크게 잡지 않는다. */
        const val SIZE = 256

        /**
         * 받는 파일의 상한.
         *
         * 다시 인코딩하므로 **저장되는 크기와는 무관하다.** 이 값은 "디코딩에 쓸 메모리를
         * 얼마나 허용할 것인가" 다 — 거대한 이미지는 디코딩 자체가 공격이 된다.
         */
        const val MAX_UPLOAD_BYTES = 5 * 1024 * 1024

        const val CONTENT_TYPE = "image/png"

        private const val HASH_LENGTH = 32
    }
}

data class ProcessedImage(val bytes: ByteArray, val contentType: String) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is ProcessedImage && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = bytes.contentHashCode()
}
