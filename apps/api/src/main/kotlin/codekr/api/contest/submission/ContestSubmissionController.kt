package codekr.api.contest.submission

import codekr.api.auth.security.AuthPrincipal
import codekr.api.submission.dto.SubmitRequest
import codekr.api.submission.dto.SubmitResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 대회 제출 (#62).
 *
 * 평소 제출과 **경로를 나눈다.** 같은 경로에 대회 여부를 실어 보내면, 대회가 아닌 척
 * 제출해 평소 큐로 보내는 길이 생긴다.
 */
@RestController
@RequestMapping("/api/v1/contests/{contestSlug}/problems/{problemSlug}/submissions")
class ContestSubmissionController(private val contestSubmissionService: ContestSubmissionService) {

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun submit(
        @PathVariable contestSlug: String,
        @PathVariable problemSlug: String,
        @RequestBody @Valid request: SubmitRequest,
        principal: AuthPrincipal,
    ): SubmitResponse = contestSubmissionService.submit(contestSlug, problemSlug, principal.userId, request)
}
