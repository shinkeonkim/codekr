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
import codekr.api.user.suspension.SuspensionResponse
import codekr.api.user.suspension.UserSuspensionRepository
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/** 어드민 회원 조회 (#223). 쓰기(역할·강제 탈퇴)는 다른 서비스가 맡는다. */
@Service
@Transactional(readOnly = true)
class AdminUserService(
    private val userRepository: UserRepository,
    private val searchRepository: AdminUserSearchRepository,
    private val scoreRepository: UserProblemScoreRepository,
    private val suspensionRepository: UserSuspensionRepository,
    private val clock: Clock,
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
        // 한 줄씩 묻지 않고 이 쪽만 한 번에 읽는다 — 목록은 스무 줄이고, 정지된 사람은
        // 대개 그중 없거나 하나다.
        val ids = page.content.map { it.id }
        val active = if (ids.isEmpty()) {
            emptyMap()
        } else {
            suspensionRepository.findActiveIn(ids, clock.instant()).groupBy { it.userId }
        }
        return PageResponse.from(
            page.map { user ->
                AdminUserSummaryResponse.from(user, active[user.id].orEmpty().map { it.scope.label })
            },
        )
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
            suspensions = suspensionRepository.findActive(id, clock.instant()).map(SuspensionResponse::of),
            emailVerifiedAt = user.emailVerifiedAt,
        )
    }
}
