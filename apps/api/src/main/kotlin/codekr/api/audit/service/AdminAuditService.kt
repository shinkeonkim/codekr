package codekr.api.audit.service

import codekr.api.audit.entity.AdminAction
import codekr.api.audit.entity.AdminAuditLog
import codekr.api.audit.entity.AuditTargetType
import codekr.api.audit.repository.AdminAuditLogRepository
import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 관리 기록을 남긴다 (#225).
 *
 * ## 같은 트랜잭션에 묶는다
 *
 * 기록이 실패하면 행위도 함께 실패한다 (`Propagation.MANDATORY` — 부르는 쪽의
 * 트랜잭션 안에서만 돈다).
 *
 * 따로 두면 **기록 없는 행위**가 생기는데, 감사 기록의 뜻이 거기서 사라진다 —
 * "기록에 없으니 안 한 것" 이라고 말할 수 없게 된다. 그 대가로 기록이 가용성 경로가
 * 되는 것은 받아들인다. 같은 DB 의 한 표에 한 줄 넣는 일이라, 이것이 실패하는 상황이면
 * 행위 자체도 이미 실패한다.
 */
@Service
class AdminAuditService(private val repository: AdminAuditLogRepository) {

    @Transactional(propagation = Propagation.MANDATORY)
    fun record(
        actorId: Long,
        action: AdminAction,
        targetId: Long,
        targetLabel: String?,
        reason: String? = null,
        detail: String? = null,
    ) {
        /*
            사유는 **되돌릴 수 없는 것과 남에게 보이는 것**에만 요구한다 (#225).

            매번 적게 하면 "확인"·"." 같은 값이 쌓여 기록을 읽을 수 없게 된다 — 그때는
            사유 칸이 있다는 사실이 오히려 해롭다.
        */
        if (action.requiresReason && reason.isNullOrBlank()) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "${action.label}에는 사유가 필요합니다.")
        }

        repository.save(
            AdminAuditLog(
                actorId = actorId,
                action = action,
                targetType = AuditTargetType.USER,
                targetId = targetId,
                targetLabel = targetLabel,
                reason = reason?.trim()?.takeIf { it.isNotBlank() },
                detail = detail,
            ),
        )
    }
}
