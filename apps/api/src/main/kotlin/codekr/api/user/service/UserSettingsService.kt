package codekr.api.user.service

import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.user.dto.UserSettingsResponse
import codekr.api.user.dto.UserSettingsUpdateRequest
import codekr.api.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class UserSettingsService(private val userRepository: UserRepository) {

    fun findSettings(userId: Long): UserSettingsResponse = UserSettingsResponse.of(require(userId))

    @Transactional
    fun updateSettings(userId: Long, request: UserSettingsUpdateRequest): UserSettingsResponse {
        val user = require(userId)
        // null 은 "바꾸지 않는다" 다. 항목이 늘어도 옛 화면이 새 항목을 지우지 않는다.
        request.defaultSubmissionVisibility?.let { user.defaultSubmissionVisibility = it }

        return UserSettingsResponse.of(user)
    }

    private fun require(userId: Long) =
        userRepository.findById(userId).orElseThrow { ApiException(ErrorCode.USER_NOT_FOUND) }
}
