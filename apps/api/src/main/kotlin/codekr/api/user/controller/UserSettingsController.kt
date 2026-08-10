package codekr.api.user.controller

import codekr.api.auth.security.AuthPrincipal
import codekr.api.user.dto.UserSettingsResponse
import codekr.api.user.dto.UserSettingsUpdateRequest
import codekr.api.user.service.UserSettingsService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 내 설정 (#104). 남의 설정은 볼 수도 바꿀 수도 없다 — 경로에 사용자 지정이 없다. */
@RestController
@RequestMapping("/api/v1/users/me/settings")
class UserSettingsController(private val userSettingsService: UserSettingsService) {

    @GetMapping
    fun findSettings(principal: AuthPrincipal): UserSettingsResponse =
        userSettingsService.findSettings(principal.userId)

    @PatchMapping
    fun updateSettings(
        @Valid @RequestBody request: UserSettingsUpdateRequest,
        principal: AuthPrincipal,
    ): UserSettingsResponse = userSettingsService.updateSettings(principal.userId, request)
}
