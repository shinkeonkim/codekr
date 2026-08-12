package codekr.api.auth.controller

import codekr.api.config.security.PublicApi
import codekr.api.config.security.AuthenticatedApi
import codekr.api.auth.dto.LoginRequest
import codekr.api.auth.dto.RefreshRequest
import codekr.api.auth.dto.SignupRequest
import codekr.api.auth.dto.TokenResponse
import codekr.api.auth.dto.UserResponse
import codekr.api.auth.security.AuthPrincipal
import codekr.api.auth.service.AuthService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val authService: AuthService) {

    @PublicApi
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    fun signup(@Valid @RequestBody request: SignupRequest): TokenResponse = authService.signup(request)

    @PublicApi
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): TokenResponse = authService.login(request)

    @PublicApi
    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshRequest): TokenResponse = authService.refresh(request)

    @AuthenticatedApi
    @GetMapping("/me")
    fun me(principal: AuthPrincipal): UserResponse = authService.findMe(principal.userId)
}
