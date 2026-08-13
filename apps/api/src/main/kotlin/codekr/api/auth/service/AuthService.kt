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
    private val emailVerificationService: codekr.api.auth.email.EmailVerificationService,
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
        // **가입이 메일 때문에 실패하면 안 된다.** 발송 실패는 MailSender 안에서
        // 삼켜지고, 여기서는 토큰 발급까지만 한다 (#233).
        emailVerificationService.send(user.id, user.email)
        return issueTokens(user)
    }

    fun login(request: LoginRequest): TokenResponse {
        val user = userRepository.findByEmail(request.email)
            ?: throw ApiException(ErrorCode.INVALID_CREDENTIALS)
        // 계정 존재 여부를 구분해서 알려주지 않는다 (계정 열거 방지).
        // 탈퇴한 계정도 같은 오류로 답한다 — "탈퇴한 계정입니다" 라고 알리면
        // 그 이메일이 쓰였다는 사실이 새어 나간다 (#140).
        if (user.isWithdrawn || !passwordEncoder.matches(request.password, user.passwordHash)) {
            throw ApiException(ErrorCode.INVALID_CREDENTIALS)
        }
        return issueTokens(user)
    }

    fun refresh(request: RefreshRequest): TokenResponse {
        val principal = tokenProvider.parse(request.refreshToken, TokenType.REFRESH)
            ?: throw ApiException(ErrorCode.INVALID_TOKEN)
        val user = userRepository.findById(principal.userId)
            .orElseThrow { ApiException(ErrorCode.USER_NOT_FOUND) }
        // 탈퇴한 계정은 갱신 토큰으로도 되살아나지 않는다 (#140).
        if (user.isWithdrawn) throw ApiException(ErrorCode.INVALID_TOKEN)
        /*
            **비밀번호를 바꾸기 전에 발급된 갱신 토큰은 통하지 않는다** (#315).

            액세스 토큰은 Redis 표시로 즉시 끊기지만 그 표시는 액세스 수명만큼만 산다.
            갱신 토큰까지 막지 않으면, 남이 들어와 있어서 비밀번호를 바꾼 사람에게
            **그 사람이 계속 새 토큰을 받아 가는** 결과가 된다.
        */
        user.passwordChangedAt?.let { changed ->
            if (principal.issuedAt?.isBefore(changed) == true) throw ApiException(ErrorCode.INVALID_TOKEN)
        }
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
