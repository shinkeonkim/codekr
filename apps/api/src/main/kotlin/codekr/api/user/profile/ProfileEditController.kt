package codekr.api.user.profile

import codekr.api.auth.security.AuthPrincipal
import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.config.security.AuthenticatedApi
import codekr.api.user.repository.UserRepository
import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 내가 쓰는 프로필 (#310).
 *
 * **설정(`/users/me/settings`)과 나눈다.** 그쪽은 나만 보는 값(기본 공개 범위·테마)이고,
 * 이쪽은 **남에게 보이는 값**이다. 닉네임 변경(#307)이 오면 같은 자리에 붙는다.
 */
@RestController
@RequestMapping("/api/v1/users/me/profile")
class ProfileEditController(private val profileEditService: ProfileEditService) {

    @AuthenticatedApi
    @PatchMapping
    fun update(
        @Valid @RequestBody request: ProfileEditRequest,
        principal: AuthPrincipal,
    ): ProfileEditResponse = profileEditService.update(principal.userId, request)
}

/** 소개 문구의 길이 상한 (#310). 마이그레이션의 컬럼 길이와 같아야 한다. */
const val BIO_MAX_LENGTH = 100

data class ProfileEditRequest(
    /**
     * 소개 문구. **빈 문자열이면 지운다.**
     *
     * 설정 변경(#104)의 "null 은 안 바꾼다" 규칙을 그대로 쓴다 — 항목이 늘어도 화면이
     * 전체를 보내지 않아도 된다.
     */
    @field:Size(max = BIO_MAX_LENGTH, message = "소개는 ${BIO_MAX_LENGTH}자를 넘을 수 없습니다.")
    val bio: String? = null,
)

data class ProfileEditResponse(val bio: String?)

@Service
class ProfileEditService(private val userRepository: UserRepository) {

    @Transactional
    fun update(userId: Long, request: ProfileEditRequest): ProfileEditResponse {
        val user = userRepository.findById(userId).orElseThrow { ApiException(ErrorCode.USER_NOT_FOUND) }

        request.bio?.let { raw ->
            /*
                줄바꿈만 살리고 나머지 공백은 다듬는다.

                줄 끝의 공백이나 문서 앞뒤의 빈 줄은 화면에서 자리만 차지한다. 반대로
                줄바꿈까지 지우면 "한 줄로만 쓰라" 는 말이 되는데, 그것은 여기서 정할
                일이 아니다.
            */
            val cleaned = raw.lines().joinToString("\n") { it.trimEnd() }.trim()
            user.bio = cleaned.takeIf { it.isNotBlank() }
        }

        return ProfileEditResponse(user.bio)
    }
}
