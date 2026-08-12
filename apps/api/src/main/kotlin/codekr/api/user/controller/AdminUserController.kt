package codekr.api.user.controller

import codekr.api.common.dto.PageResponse
import codekr.api.config.security.AdminApi
import codekr.api.user.dto.AdminUserDetailResponse
import codekr.api.user.dto.AdminUserSummaryResponse
import codekr.api.user.entity.UserRole
import codekr.api.user.service.AdminUserService
import org.springframework.data.domain.PageRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

private const val MAX_PAGE_SIZE = 100

/**
 * 어드민 회원 목록·검색·상세 (#223).
 *
 * **읽기와 쓰기의 권한이 다르다.** 회원을 *찾는* 것과 역할을 *바꾸는* 것은 무게가 다르다 —
 * 여기는 `ADMIN`, 역할 변경·강제 탈퇴는 `SUPERUSER`(`AdminUserRoleController`,
 * `WithdrawalController`).
 *
 * 전에는 어드민 회원 경로가 규칙에 없어 **통째로 SUPERUSER 로 잠겨** 있었다.
 * 경로로 나누면 순서에 의존해 조용히 덮이는데, 지금은 핸들러마다 선언한다 (#198).
 */
@RestController
@RequestMapping("/api/v1/admin/users")
class AdminUserController(private val adminUserService: AdminUserService) {

    @AdminApi(UserRole.ADMIN)
    @GetMapping
    fun search(
        /**
         * 닉네임 또는 이메일. **이메일은 두 글자 이상**이어야 한다 (#223).
         *
         * 목록에 이메일을 보이기로 했으므로 부분 일치 검색이 사실상 이메일 목록을 훑는
         * 수단이 된다. 한 글자로 훑는 것만 막아도 그 값이 크게 떨어진다.
         */
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) role: UserRole?,
        /** 탈퇴한 회원은 **기본으로 뺀다.** 대부분의 조회는 살아 있는 사람을 찾는 일이다. */
        @RequestParam(defaultValue = "false") includeWithdrawn: Boolean,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<AdminUserSummaryResponse> =
        adminUserService.search(
            keyword = q,
            role = role,
            includeWithdrawn = includeWithdrawn,
            pageable = PageRequest.of(maxOf(page, 0), size.coerceIn(1, MAX_PAGE_SIZE)),
        )

    @AdminApi(UserRole.ADMIN)
    @GetMapping("/{id}")
    fun detail(@PathVariable id: Long): AdminUserDetailResponse = adminUserService.detail(id)
}
