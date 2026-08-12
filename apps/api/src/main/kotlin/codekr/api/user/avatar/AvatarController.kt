package codekr.api.user.avatar

import codekr.api.config.security.AuthenticatedApi
import codekr.api.auth.security.AuthPrincipal
import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/** 내 아바타 (#116). **본인 것만** — 경로에 남을 지정할 방법이 없다. */
@RestController
@RequestMapping("/api/v1/users/me/avatar")
class AvatarController(private val avatarService: AvatarService) {

    @AuthenticatedApi
    @PutMapping
    fun replace(
        @RequestPart("file") file: MultipartFile,
        principal: AuthPrincipal,
    ): AvatarResponse {
        if (file.isEmpty) throw ApiException(ErrorCode.VALIDATION_ERROR, "파일이 비어 있습니다.")
        // 확장자와 선언된 Content-Type 을 믿지 않는다. 실제 내용으로 판별한다 (#115).
        val key = avatarService.replace(principal.userId, file.bytes)
        return AvatarResponse(AvatarService.urlOf(key))
    }

    @AuthenticatedApi
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun remove(principal: AuthPrincipal) = avatarService.remove(principal.userId)
}

data class AvatarResponse(val avatarUrl: String?)
