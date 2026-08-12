package codekr.api.audit.dto

import codekr.api.audit.entity.AdminAuditLog
import java.time.Instant

/** 관리 기록 한 줄 (#225). */
data class AdminAuditLogResponse(
    val id: Long,
    val actorId: Long,
    /** 그 어드민의 지금 닉네임. 탈퇴했으면 없다. */
    val actorNickname: String?,
    val action: String,
    val actionLabel: String,
    val targetId: Long,
    /** **그때의** 대상 이름 (#140 이 지운 뒤에도 남는다). */
    val targetLabel: String?,
    val reason: String?,
    val detail: String?,
    val createdAt: Instant,
) {
    companion object {
        fun of(log: AdminAuditLog, actorNickname: String?) = AdminAuditLogResponse(
            id = log.id,
            actorId = log.actorId,
            actorNickname = actorNickname,
            action = log.action.name,
            actionLabel = log.action.label,
            targetId = log.targetId,
            targetLabel = log.targetLabel,
            reason = log.reason,
            detail = log.detail,
            createdAt = log.createdAt,
        )
    }
}
