package codekr.api.board.attachment

import codekr.api.auth.security.AuthPrincipal
import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.config.security.AuthenticatedApi
import codekr.api.storage.ImagePolicy
import codekr.api.storage.ImageProcessor
import codekr.api.storage.ObjectStorage
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.time.Duration
import java.time.Instant

/**
 * 글에 붙일 이미지를 올린다 (#389).
 *
 * "이 코드가 왜 틀렸나요" 는 코드 블록으로 되지만 **오류 화면·그림·표는 안 됐다.**
 * 그래서 남의 이미지 호스팅에 올리고 링크를 붙이는 수밖에 없었고, **그 링크는 언젠가
 * 깨진다.**
 *
 * **정지된 사람과 주소를 확인하지 않은 사람은 못 올린다.** 경로를 따로 적지 않아도
 * 그렇게 된다 — `SuspendedAction` 이 안 적힌 쓰기를 전부 `WRITE` 로 보고(#224),
 * 이메일 인증 판정이 같은 것을 쓴다(#233). **빠뜨릴 수 없는 구조**다.
 */
@RestController
@RequestMapping("/api/v1/attachments")
class AttachmentController(private val service: AttachmentService) {

    @AuthenticatedApi
    @PostMapping
    fun upload(
        @RequestPart("file") file: MultipartFile,
        principal: AuthPrincipal,
    ): AttachmentResponse {
        if (file.isEmpty) throw ApiException(ErrorCode.VALIDATION_ERROR, "파일이 비어 있습니다.")
        // 확장자와 선언된 Content-Type 을 믿지 않는다. 실제 내용으로 판별한다 (#115).
        return service.upload(principal.userId, file.bytes)
    }
}

data class AttachmentResponse(val url: String)

@Service
class AttachmentService(
    private val storage: ObjectStorage,
    private val imageProcessor: ImageProcessor,
    private val attachments: PostAttachmentRepository,
) {

    @Transactional
    fun upload(userId: Long, bytes: ByteArray): AttachmentResponse {
        if (!storage.available) throw ApiException(ErrorCode.STORAGE_UNAVAILABLE)
        requireWithinDailyLimit(userId)

        // 다시 인코딩하는 과정이 곧 검증이다 (#115). 상한(용량·픽셀)은 그 안에서 걸린다.
        val image = imageProcessor.process(bytes, ImagePolicy.ATTACHMENT)
        // 확장자가 정책에서 온다 — 첨부는 jpg 다. 기본값(png)을 쓰면 주소와 내용이 어긋난다.
        val key = imageProcessor.keyFor(PREFIX, image.bytes, ImagePolicy.ATTACHMENT)
        storage.put(key, image.bytes, image.contentType)

        // 키는 내용 해시라 같은 그림을 다시 올리면 같다. 행을 두 번 만들지 않는다.
        if (attachments.findByStorageKey(key) == null) {
            attachments.save(PostAttachment(userId, key, image.bytes.size))
        }
        return AttachmentResponse(urlOf(key))
    }

    /**
     * 하루에 올릴 수 있는 장수 (#389).
     *
     * **로그인만 하면 누구나 파일을 올릴 수 있는 경로**라, 크기 제한만으로는 디스크가
     * 사용자 입력에 묶인다 (#281 이 백업 용량에서 만난 것과 같은 결).
     *
     * 글마다 세지 않는 이유: 올리는 시점에는 어느 글에 붙을지 모른다. **글에 몇 장까지
     * 넣을 수 있는지는 글을 저장할 때** 따로 센다.
     */
    private fun requireWithinDailyLimit(userId: Long) {
        val since = Instant.now().minus(Duration.ofDays(1))
        if (attachments.countByUploaderIdAndCreatedAtAfter(userId, since) >= DAILY_LIMIT) {
            throw ApiException(ErrorCode.TOO_MANY_REQUESTS, "오늘은 이미지를 ${DAILY_LIMIT}장까지 올릴 수 있습니다.")
        }
    }

    companion object {
        private const val PREFIX = "attachments"

        /** 하루 상한. 질문 하나에 서너 장이면 충분하고, 그보다 훨씬 넉넉하다. */
        const val DAILY_LIMIT = 50L

        /** 한 글에 넣을 수 있는 장수. 크기 제한만으로는 안 막힌다 (#389). */
        const val PER_POST_LIMIT = 10

        /** 키를 화면이 쓸 주소로 바꾼다. **한 곳에서만 만든다** (아바타와 같은 규칙). */
        fun urlOf(key: String) = "/api/v1/files/$key"

        /** 본문에 우리 이미지가 몇 장 들어 있나. 화면이 아니라 서버가 센다. */
        fun countIn(body: String) = Regex("""!\[[^\]]*]\(/api/v1/files/$PREFIX/""").findAll(body).count()
    }
}
