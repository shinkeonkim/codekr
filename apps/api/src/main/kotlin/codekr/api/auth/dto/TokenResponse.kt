package codekr.api.auth.dto

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: UserResponse,
)
