package codekr.api.user.suspension

import codekr.api.auth.security.AuthPrincipal
import codekr.api.config.security.AdminApi
import codekr.api.user.entity.UserRole
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/**
 * 회원 정지 (#224).
 *
 * 회원 관리 화면(#223)에 붙는다. **`ADMIN` 이 건다** — 강제 탈퇴(`SUPERUSER`)와 달리
 * 되돌릴 수 있는 조치라, 실제로 게시판을 지키는 사람이 쓸 수 있어야 한다.
 */
@RestController
@RequestMapping("/api/v1/admin/users/{userId}/suspensions")
class AdminSuspensionController(private val suspensionService: SuspensionService) {

    @GetMapping
    @AdminApi(UserRole.ADMIN)
    fun active(@PathVariable userId: Long): List<SuspensionResponse> =
        suspensionService.activeOf(userId).map(SuspensionResponse::of)

    @PostMapping
    @AdminApi(UserRole.ADMIN)
    @ResponseStatus(HttpStatus.CREATED)
    fun suspend(
        @PathVariable userId: Long,
        @RequestBody request: SuspendRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): SuspensionResponse = SuspensionResponse.of(
        suspensionService.suspend(principal.userId, userId, request.scope, request.reason, request.days),
    )

    @DeleteMapping("/{suspensionId}")
    @AdminApi(UserRole.ADMIN)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun lift(
        @PathVariable userId: Long,
        @PathVariable suspensionId: Long,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ) = suspensionService.lift(principal.userId, suspensionId)
}

data class SuspendRequest(
    val scope: SuspensionScope,
    @field:NotBlank val reason: String,
    /** null 이면 기한 없음. 어드민이 풀기 전까지 이어진다. */
    val days: Int? = null,
)

data class SuspensionResponse(
    val id: Long,
    val scope: SuspensionScope,
    val scopeLabel: String,
    val reason: String,
    val endsAt: Instant?,
    val createdAt: Instant,
) {
    companion object {
        fun of(suspension: UserSuspension) = SuspensionResponse(
            id = suspension.id,
            scope = suspension.scope,
            scopeLabel = suspension.scope.label,
            reason = suspension.reason,
            endsAt = suspension.endsAt,
            createdAt = suspension.createdAt,
        )
    }
}
