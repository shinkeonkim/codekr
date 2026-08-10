package codekr.api.user.avatar

import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.storage.ImageProcessor
import codekr.api.storage.ObjectStorage
import codekr.api.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 프로필 아바타 (#116).
 *
 * 올린 이미지는 정사각 PNG 로 다시 만들어 저장한다 (#115) — 그 과정이 곧 검증이다.
 */
@Service
@Transactional
class AvatarService(
    private val userRepository: UserRepository,
    private val storage: ObjectStorage,
    private val imageProcessor: ImageProcessor,
) {

    fun replace(userId: Long, bytes: ByteArray): String {
        if (!storage.available) throw ApiException(ErrorCode.STORAGE_UNAVAILABLE)
        val user = userRepository.findById(userId).orElseThrow { ApiException(ErrorCode.USER_NOT_FOUND) }

        val image = imageProcessor.toSquarePng(bytes)
        val key = imageProcessor.keyFor(PREFIX, image.bytes)
        storage.put(key, image.bytes, image.contentType)

        // 옛 이미지를 지운다. 참조가 끊긴 파일이 쌓이지 않게 (#115).
        // 같은 이미지를 다시 올리면 키가 같으므로 방금 올린 것을 지우지 않도록 확인한다.
        val previous = user.avatarKey
        user.avatarKey = key
        if (previous != null && previous != key) storage.delete(previous)

        return key
    }

    fun remove(userId: Long) {
        val user = userRepository.findById(userId).orElseThrow { ApiException(ErrorCode.USER_NOT_FOUND) }
        val key = user.avatarKey ?: return
        user.avatarKey = null
        storage.delete(key)
    }

    companion object {
        private const val PREFIX = "avatars"

        /** 키를 화면이 쓸 주소로 바꾼다. **한 곳에서만 만든다.** */
        fun urlOf(key: String?): String? = key?.let { "/api/v1/files/$it" }
    }
}
