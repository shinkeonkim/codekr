package codekr.api.user.dto

import codekr.api.user.entity.User
import codekr.api.user.entity.UserRole
import codekr.api.user.suspension.SuspensionResponse
import java.time.Instant

/**
 * 어드민 회원 목록의 한 줄 (#223).
 *
 * **id 와 이메일을 함께 보인다.** 사람을 특정하려면 둘 다 필요하고, 재계산 같은 작업이
 * id 를 요구한다 — 목록에서 바로 집어낼 수 있어야 DB 를 열지 않는다.
 */
data class AdminUserSummaryResponse(
    val id: Long,
    val email: String,
    val nickname: String,
    val roles: Set<UserRole>,
    val createdAt: Instant,
    /** 탈퇴한 회원이면 그 시각. 목록에서 기본으로 빠지지만 켜면 보인다. */
    val withdrawnAt: Instant?,
    /**
     * 지금 걸려 있는 정지의 범위 (#224). 없으면 비어 있다.
     *
     * **목록에서 보여야 한다** — 상세를 하나씩 열어 봐야 안다면, 어드민은 이미 정지된
     * 사람을 또 정지시킨다.
     */
    val suspendedScopes: List<String> = emptyList(),
) {
    companion object {
        fun from(user: User, suspendedScopes: List<String> = emptyList()) = AdminUserSummaryResponse(
            id = user.id,
            email = user.email,
            nickname = user.nickname,
            roles = user.roles,
            createdAt = user.createdAt,
            withdrawnAt = user.withdrawnAt,
            suspendedScopes = suspendedScopes,
        )
    }
}

/**
 * 한 사람의 상태를 한 화면에서 (#223).
 *
 * 점수·푼 문제 수는 랭킹 표에서 온다 (#57). **여기서 다시 세지 않는다** — 세는 규칙이
 * 두 곳에 생기면 프로필과 어드민 화면의 숫자가 갈린다.
 */
data class AdminUserDetailResponse(
    val id: Long,
    val email: String,
    val nickname: String,
    val roles: Set<UserRole>,
    val createdAt: Instant,
    val withdrawnAt: Instant?,
    val score: Int,
    val solvedCount: Int,
    val submissionCount: Int,
    /** 마지막 제출 시각. 활동이 멈춘 계정을 가려낼 때 쓴다. */
    val lastSubmittedAt: Instant?,
    /** 지금 걸려 있는 정지들 (#224). 여기서 바로 풀 수 있어야 한다. */
    val suspensions: List<SuspensionResponse> = emptyList(),
)
