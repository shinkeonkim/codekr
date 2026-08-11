package codekr.api.storage

import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import org.springframework.stereotype.Component
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.HexFormat
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 올린 이미지를 안전한 형태로 다시 만든다 (#115).
 *
 * **확장자를 믿지 않는다.** 디코딩에 성공해야 이미지다 — `.png` 로 이름만 바꾼
 * 실행 파일은 여기서 걸린다.
 *
 * 다시 인코딩하는 것이 핵심이다. 그 결과로 네 가지가 한꺼번에 해결된다.
 *   1. 내용 기반 검증 (디코딩 실패 = 이미지 아님)
 *   2. 출력 크기가 입력과 무관하게 정해진다 — 목록에서 수 MB 를 내려받는 일이 없다
 *   3. EXIF 와 그 안에 숨은 것이 사라진다. 사진의 촬영 위치가 아바타로 새지 않는다
 *   4. 용도별 규격([ImagePolicy])이 한 곳에서 강제된다
 */
@Component
class ImageProcessor {

    /**
     * 정책에 맞춰 다시 인코딩한다.
     *
     * **거절은 디코딩보다 먼저 한다.** 용량과 픽셀 수를 헤더만 보고 판단하므로, 거대한
     * 이미지가 메모리를 잡기 전에 막힌다.
     */
    fun process(input: ByteArray, policy: ImagePolicy): ProcessedImage {
        requireWithinLimits(input)

        val source = runCatching { ImageIO.read(ByteArrayInputStream(input)) }.getOrNull()
            ?: throw ApiException(ErrorCode.VALIDATION_ERROR, "이미지 파일이 아닙니다.")

        val target = if (policy.squareCrop) squareCrop(source) else source
        val resized = scaleWithin(target, policy)
        return ProcessedImage(encode(resized, policy), policy.contentType)
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
    fun keyFor(prefix: String, bytes: ByteArray, policy: ImagePolicy = ImagePolicy.AVATAR): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return "$prefix/${HexFormat.of().formatHex(digest).take(HASH_LENGTH)}.${policy.extension}"
    }

    /** 용량과 픽셀 수를 **디코딩 전에** 확인한다. */
    private fun requireWithinLimits(input: ByteArray) {
        if (input.size > ImageLimits.MAX_UPLOAD_BYTES) {
            throw ApiException(
                ErrorCode.IMAGE_TOO_LARGE,
                "이미지는 ${ImageLimits.MAX_UPLOAD_BYTES / 1024 / 1024}MB 이하여야 합니다.",
            )
        }

        val (width, height) = readDimensions(input)
            ?: throw ApiException(ErrorCode.VALIDATION_ERROR, "이미지 파일이 아닙니다.")
        if (width.toLong() * height > ImageLimits.MAX_PIXELS) {
            throw ApiException(
                ErrorCode.IMAGE_TOO_LARGE,
                "이미지가 너무 큽니다 (${width}x$height). 가로×세로가 ${ImageLimits.MAX_PIXELS / 1_000_000}백만 화소를 넘을 수 없습니다.",
            )
        }
    }

    /** 헤더만 읽어 크기를 알아낸다. 픽셀을 메모리에 올리지 않는다. */
    private fun readDimensions(input: ByteArray): Pair<Int, Int>? =
        ImageIO.createImageInputStream(ByteArrayInputStream(input))?.use { stream ->
            val reader = ImageIO.getImageReaders(stream).asSequence().firstOrNull() ?: return null
            try {
                reader.input = stream
                reader.getWidth(0) to reader.getHeight(0)
            } catch (_: Exception) {
                null
            } finally {
                reader.dispose()
            }
        }

    /** 가운데를 기준으로 자른다 — 얼굴 사진이 대부분이고, 가운데가 잘려 나가는 경우는 드물다. */
    private fun squareCrop(source: BufferedImage): BufferedImage {
        val side = min(source.width, source.height)
        return source.getSubimage((source.width - side) / 2, (source.height - side) / 2, side, side)
    }

    /**
     * 최대 변에 맞춰 줄인다. **키우지는 않는다** — 작은 이미지를 늘리면 흐려지기만 하고
     * 용량은 늘어난다.
     */
    private fun scaleWithin(source: BufferedImage, policy: ImagePolicy): BufferedImage {
        val ratio = minOf(
            policy.maxEdge.toDouble() / source.width,
            policy.maxEdge.toDouble() / source.height,
            1.0,
        )
        val width = (source.width * ratio).roundToInt().coerceAtLeast(1)
        val height = (source.height * ratio).roundToInt().coerceAtLeast(1)

        // JPEG 은 알파가 없다. ARGB 로 그리면 투명한 곳이 검게 나오므로 흰색을 깐다.
        val type = if (policy.quality == null) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
        val target = BufferedImage(width, height, type)
        target.createGraphics().apply {
            if (type == BufferedImage.TYPE_INT_RGB) {
                color = Color.WHITE
                fillRect(0, 0, width, height)
            }
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            drawImage(source, 0, 0, width, height, null)
            dispose()
        }
        return target
    }

    private fun encode(image: BufferedImage, policy: ImagePolicy): ByteArray {
        val output = ByteArrayOutputStream()
        val quality = policy.quality
            ?: return output.also { ImageIO.write(image, policy.format, it) }.toByteArray()

        val writer = ImageIO.getImageWritersByFormatName(policy.format).next()
        ImageIO.createImageOutputStream(output).use { stream ->
            writer.output = stream
            val params = writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = quality
            }
            writer.write(null, IIOImage(image, null, null), params)
        }
        writer.dispose()
        return output.toByteArray()
    }

    private companion object {
        const val HASH_LENGTH = 32
    }
}

data class ProcessedImage(val bytes: ByteArray, val contentType: String) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is ProcessedImage && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = bytes.contentHashCode()
}
