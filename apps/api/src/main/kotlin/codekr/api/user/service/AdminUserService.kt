package codekr.api.user.service

import codekr.api.common.dto.PageResponse
import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.ranking.repository.UserProblemScoreRepository
import codekr.api.user.dto.AdminUserDetailResponse
import codekr.api.user.dto.AdminUserSummaryResponse
import codekr.api.user.entity.UserRole
import codekr.api.user.repository.AdminUserSearchRepository
import codekr.api.user.repository.UserRepository
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 어드민 회원 조회 (#223). 쓰기(역할·강제 탈퇴)는 다른 서비스가 맡는다. */
@Service
@Transactional(readOnly = true)
class AdminUserService(
    private val userRepository: UserRepository,
    private val searchRepository: AdminUserSearchRepository,
    private val scoreRepository: UserProblemScoreRepository,
) {

    /** 이메일 검색의 최소 글자 수. 한 글자로 이메일 목록을 훑는 것을 막는다. */
    private val minKeywordLength = 2

    fun search(
        keyword: String?,
        role: UserRole?,
        includeWithdrawn: Boolean,
        pageable: Pageable,
    ): PageResponse<AdminUserSummaryResponse> {
        val trimmed = keyword?.trim()?.takeIf { it.isNotBlank() }
        if (trimmed != null && trimmed.length < minKeywordLength) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "검색어는 ${minKeywordLength}글자 이상이어야 합니다.")
        }

        val page = searchRepository.search(trimmed, role, includeWithdrawn, pageable)
        return PageResponse.from(page.map(AdminUserSummaryResponse::from))
    }

    fun detail(id: Long): AdminUserDetailResponse {
        val user = userRepository.findById(id).orElseThrow { ApiException(ErrorCode.USER_NOT_FOUND) }
        val (score, solvedCount) = scoreRepository.totalsOf(id)
        val activity = searchRepository.activityOf(id)

        return AdminUserDetailResponse(
            id = user.id,
            email = user.email,
            nickname = user.nickname,
            roles = user.roles,
            createdAt = user.createdAt,
            withdrawnAt = user.withdrawnAt,
            score = score,
            solvedCount = solvedCount,
            submissionCount = activity.submissionCount,
            lastSubmittedAt = activity.lastSubmittedAt,
        )
    }
}
