package codekr.api.rejudge.controller

import codekr.api.auth.security.AuthPrincipal
import codekr.api.rejudge.dto.RejudgeRequest
import codekr.api.rejudge.dto.RejudgeResponse
import codekr.api.rejudge.service.RejudgeService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 문제 단위 재채점 (#107). 문제 경로 아래라 PROBLEM_SETTER 권한이 적용된다 (#103). */
@RestController
@RequestMapping("/api/v1/admin/problems")
class AdminRejudgeController(private val rejudgeService: RejudgeService) {

    @PostMapping("/{id}/rejudge")
    fun rejudge(
        @PathVariable id: Long,
        @Valid @RequestBody request: RejudgeRequest,
        principal: AuthPrincipal,
    ): RejudgeResponse = rejudgeService.rejudgeProblem(id, request.reason, principal.userId)
}
