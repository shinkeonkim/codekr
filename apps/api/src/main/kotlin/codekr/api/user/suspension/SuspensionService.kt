package codekr.api.user.suspension

import codekr.api.audit.entity.AdminAction
import codekr.api.audit.service.AdminAuditService
import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.notification.entity.NotificationCategory
import codekr.api.notification.service.NotificationService
import codekr.api.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration

/** 회원 정지를 걸고 푼다 (#224). */
@Service
class SuspensionService(
    private val suspensions: UserSuspensionRepository,
    private val users: UserRepository,
    private val auditService: AdminAuditService,
    private val notificationService: NotificationService,
    private val clock: Clock,
) {

    @Transactional
    fun suspend(actorId: Long, userId: Long, scope: SuspensionScope, reason: String, days: Int?): UserSuspension {
        val user = users.findById(userId).orElseThrow { ApiException(ErrorCode.USER_NOT_FOUND) }
        if (reason.isBlank()) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "정지 사유를 적어야 합니다.")
        }
        // **기한 없는 정지는 강제 탈퇴와 구분이 흐려진다.** 그래도 남겨 두는 이유는
        // 되돌릴 수 있고 기록이 남기 때문이다 — 지우는 것은 마지막 수단으로 둔다 (#140).
        if (days != null && days <= 0) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "정지 기간은 하루 이상이어야 합니다.")
        }

        val saved = suspensions.save(
            UserSuspension(
                userId = userId,
                scope = scope,
                reason = reason.trim(),
                endsAt = days?.let { clock.instant().plus(Duration.ofDays(it.toLong())) },
                createdBy = actorId,
            ),
        )

        /*
            **본인에게 알린다** (#106).

            막혔을 때만 보이게 두면, 정지된 줄 모르고 긴 글을 다 쓴 뒤에야 알게 된다.
            알림은 끌 수 없으므로(#199) 반드시 닿는다 — 남에게 보이는 조치에는 그
            성질이 맞다.
        */
        notificationService.notify(
            userId = userId,
            category = NotificationCategory.SYSTEM,
            title = "${scope.label}가 제한되었습니다",
            body = "사유: ${reason.trim()}${days?.let { " · ${it}일" } ?: " · 기한 없음"}",
        )

        auditService.record(
            actorId = actorId,
            action = AdminAction.SUSPEND,
            targetId = userId,
            targetLabel = user.nickname,
            reason = reason.trim(),
            detail = "${scope.label} · ${days?.let { "${it}일" } ?: "기한 없음"}",
        )
        return saved
    }

    @Transactional
    fun lift(actorId: Long, suspensionId: Long) {
        val suspension = suspensions.findById(suspensionId)
            .orElseThrow { ApiException(ErrorCode.SUSPENSION_NOT_FOUND) }
        val user = users.findById(suspension.userId).orElseThrow { ApiException(ErrorCode.USER_NOT_FOUND) }

        suspension.lift(actorId, clock.instant())

        notificationService.notify(
            userId = suspension.userId,
            category = NotificationCategory.SYSTEM,
            title = "${suspension.scope.label} 제한이 풀렸습니다",
        )

        auditService.record(
            actorId = actorId,
            action = AdminAction.LIFT_SUSPENSION,
            targetId = suspension.userId,
            targetLabel = user.nickname,
            detail = suspension.scope.label,
        )
    }

    /** 화면이 "지금 정지 중인가" 를 묻는 자리. 기한이 지난 것은 여기서 이미 빠진다. */
    @Transactional(readOnly = true)
    fun activeOf(userId: Long): List<UserSuspension> = suspensions.findActive(userId, clock.instant())
}
