package codekr.api.user.controller

import codekr.api.config.security.AdminApi
import codekr.api.auth.security.AuthPrincipal
import codekr.api.user.dto.RoleChangeRequest
import codekr.api.user.entity.UserRole
import codekr.api.user.service.UserRoleService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 역할 부여/회수 (#103).
 *
 * 경로 규칙에 따로 적지 않았으므로 **최고 관리자만** 부를 수 있다 —
 * SecurityConfig 의 안전한 기본값이 그렇게 정한다.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
class AdminUserRoleController(private val userRoleService: UserRoleService) {

    @AdminApi(UserRole.SUPERUSER)
    @PutMapping("/{id}/roles")
    fun replaceRoles(
        @PathVariable id: Long,
        @Valid @RequestBody request: RoleChangeRequest,
        principal: AuthPrincipal,
    ): Set<UserRole> = userRoleService.replaceRoles(id, request.roles, principal.userId)
}
