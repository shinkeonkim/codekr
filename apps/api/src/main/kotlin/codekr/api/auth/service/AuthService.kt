package codekr.api.auth.service

import codekr.api.auth.dto.LoginRequest
import codekr.api.auth.dto.RefreshRequest
import codekr.api.auth.dto.SignupRequest
import codekr.api.auth.dto.TokenResponse
import codekr.api.auth.dto.UserResponse
import codekr.api.auth.security.JwtTokenProvider
import codekr.api.auth.security.TokenType
import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.user.entity.User
import codekr.api.user.repository.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokenProvider: JwtTokenProvider,
) {

    @Transactional
    fun signup(request: SignupRequest): TokenResponse {
        if (userRepository.existsByEmail(request.email)) throw ApiException(ErrorCode.EMAIL_ALREADY_EXISTS)
        if (userRepository.existsByNickname(request.nickname)) throw ApiException(ErrorCode.NICKNAME_ALREADY_EXISTS)

        val user = userRepository.save(
            User(
                email = request.email,
                passwordHash = hashPassword(request.password),
                nickname = request.nickname,
            ),
        )
        return issueTokens(user)
    }

    fun login(request: LoginRequest): TokenResponse {
        val user = userRepository.findByEmail(request.email)
            ?: throw ApiException(ErrorCode.INVALID_CREDENTIALS)
        // 계정 존재 여부를 구분해서 알려주지 않는다 (계정 열거 방지).
        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw ApiException(ErrorCode.INVALID_CREDENTIALS)
        }
        return issueTokens(user)
    }

    fun refresh(request: RefreshRequest): TokenResponse {
        val principal = tokenProvider.parse(request.refreshToken, TokenType.REFRESH)
            ?: throw ApiException(ErrorCode.INVALID_TOKEN)
        val user = userRepository.findById(principal.userId)
            .orElseThrow { ApiException(ErrorCode.USER_NOT_FOUND) }
        return issueTokens(user)
    }

    fun findMe(userId: Long): UserResponse = UserResponse.from(
        userRepository.findById(userId).orElseThrow { ApiException(ErrorCode.USER_NOT_FOUND) },
    )

    /** PasswordEncoder 계약상 null 이 올 수 없지만 Kotlin 타입으로는 nullable 이라 여기서 좁힌다. */
    private fun hashPassword(raw: String): String =
        passwordEncoder.encode(raw) ?: throw ApiException(ErrorCode.INTERNAL_ERROR)

    private fun issueTokens(user: User) = TokenResponse(
        accessToken = tokenProvider.issueAccessToken(user),
        refreshToken = tokenProvider.issueRefreshToken(user),
        user = UserResponse.from(user),
    )
}
