package codekr.api.problem.draft

import codekr.api.audit.entity.AdminAction
import codekr.api.audit.service.AdminAuditService
import codekr.api.auth.security.AuthPrincipal
import codekr.api.config.security.AdminApi
import codekr.api.user.entity.UserRole
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 지문에서 초안 만들기 (#230).
 *
 * **문제를 만들지 않는다.** 초안을 돌려줄 뿐이고, 저장은 기존 등록 경로로 사람이 한다.
 * 그래서 이 경로에는 문제 id 도 없고 아무것도 바뀌지 않는다.
 */
@RestController
@RequestMapping("/api/v1/admin/problems/draft")
class AdminProblemDraftController(private val draftFacade: ProblemDraftFacade) {

    @AdminApi(UserRole.PROBLEM_SETTER)
    @PostMapping
    fun draft(
        @RequestBody @Valid request: ProblemDraftRequest,
        principal: AuthPrincipal,
    ): ProblemDraftResponse = draftFacade.draft(principal.userId, request.statement)
}

data class ProblemDraftRequest(
    @field:NotBlank(message = "지문을 붙여 넣어 주세요.")
    val statement: String = "",
)

/**
 * 초안 만들기와 **그 사실을 남기는 것**을 함께 묶는다 (#230, #225).
 *
 * 무엇을 보냈고 무엇이 왔는지 남긴다. 바깥으로 나가고 돈이 드는 호출이라, 나중에
 * "누가 이걸 몇 번 불렀나" 를 물을 자리가 있어야 한다.
 *
 * **지문 전체를 기록에 넣지 않는다.** 길고, 남의 저작물일 수 있으며(#236), 기록은
 * 지우기 어렵다. 길이와 뽑아낸 결과의 요약만 남긴다.
 */
@Service
class ProblemDraftFacade(
    private val service: ProblemDraftService,
    private val auditService: AdminAuditService,
) {

    @Transactional
    fun draft(actorId: Long, statement: String): ProblemDraftResponse {
        val draft = service.draft(actorId, statement)
        auditService.record(
            actorId = actorId,
            action = AdminAction.PROBLEM_DRAFT,
            // 만들어진 문제가 없으므로 가리킬 대상이 없다.
            targetId = 0,
            targetLabel = draft.title,
            detail = "지문 ${statement.length}자 · 예제 ${draft.examples.size}개" +
                (draft.missing.takeIf { it.isNotEmpty() }?.let { " · 못 찾음: ${it.joinToString(", ")}" } ?: ""),
        )
        return draft
    }
}
