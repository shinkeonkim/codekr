package codekr.api.problem.admin.service

import codekr.api.audit.entity.AdminAction
import codekr.api.audit.entity.AuditTargetType
import codekr.api.audit.service.AdminAuditService
import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.problem.repository.ProblemRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 공개 여부만 바꾼다 (#627).
 *
 * **`update` 로는 이것을 할 수 없었다.** 그 경로는 문제 전체를 덮어쓰는 upsert 라,
 * 체크 하나를 뒤집어도 테스트케이스·템플릿·실행 제한이 통째로 지워졌다 다시 들어간다.
 * 묶음 올리기(#479)가 테스트케이스 수백 개짜리 문제를 위해 있는데, **그 문제를 공개하는
 * 값이 가장 비쌌다.** 게다가 화면에 있는 값으로 덮으므로 폼이 모르는 필드는 조용히 날아간다.
 *
 * 수량도 문제였다. 묶음은 언제나 초안으로 들어오고(#479) #605 때 스물다섯 개가 한 번에
 * 들어왔다 — **올리기는 일괄인데 공개만 한 건씩**이었다.
 */
@Service
class ProblemPublishService(
    private val problemRepository: ProblemRepository,
    private val auditService: AdminAuditService,
) {

    @Transactional
    fun publish(actorId: Long, ids: List<Long>, published: Boolean): PublishResult {
        val unique = ids.distinct()
        if (unique.isEmpty()) throw ApiException(ErrorCode.VALIDATION_ERROR, "바꿀 문제를 고르지 않았습니다.")
        if (unique.size > MAX_AT_ONCE) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "한 번에 ${MAX_AT_ONCE}개까지 바꿀 수 있습니다.")
        }

        val problems = unique.mapNotNull(problemRepository::findByIdAndDeletedAtIsNull)
        // **없는 것을 조용히 넘기지 않는다.** 20개를 골랐는데 18개만 바뀌었다면
        // 화면은 그것을 말해야 한다 — 안 그러면 지워진 문제를 계속 고르고 있게 된다.
        val missing = unique - problems.map { it.id }.toSet()

        val changed = problems.filter { it.published != published }
        changed.forEach { problem ->
            problem.published = published
            /*
                **문제마다 남긴다.** 기록의 색인이 `(target_type, target_id)` 라
                "이 문제에 무슨 일이 있었나" 를 묻는 자리다 — 스무 건을 한 줄로 남기면
                나머지 열아홉 문제에 대해서는 아무 답도 없다.
            */
            auditService.record(
                actorId = actorId,
                action = AdminAction.PROBLEM_PUBLISH,
                targetType = AuditTargetType.PROBLEM,
                targetId = problem.id,
                targetLabel = problem.title,
                detail = (if (published) "공개" else "비공개") +
                    (if (changed.size > 1) " · 일괄 ${changed.size}건" else ""),
            )
        }

        return PublishResult(requested = unique.size, changed = changed.size, missing = missing.sorted())
    }

    companion object {
        /**
         * 한 번에 바꿀 수 있는 수.
         *
         * 화면 한 장이 20건이고(#625), 여러 장을 모아 고르는 흐름은 아직 없다. 상한이
         * 없으면 **되돌리기 어려운 실수의 크기에 한계가 없어진다.**
         */
        const val MAX_AT_ONCE = 100
    }
}

/**
 * 몇 개를 바꿨나.
 *
 * `requested` 와 `changed` 가 다른 것은 **이상한 일이 아니다** — 이미 공개된 것을
 * 함께 골랐으면 그것은 바뀌지 않는다. 화면이 "3건을 공개했습니다" 라고 말할 수 있게,
 * **실제로 바뀐 수**를 따로 돌려준다.
 */
data class PublishResult(
    val requested: Int,
    val changed: Int,
    val missing: List<Long>,
)
