package codekr.api.problem.quiz

import codekr.api.auth.security.AuthPrincipal
import codekr.api.config.security.AuthenticatedApi
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 퀴즈 답 제출 (#650).
 *
 * **경로를 `/submissions` 와 나눈다.** 몸통이 다르고(소스 코드가 아니라 고른 번호),
 * 응답도 다르다 — 저쪽은 `202` 로 접수만 하고 판정은 소켓으로 오는데, 여기는
 * **그 자리에서 채점이 끝난다.** 한 경로에 담으면 요청 몸통과 응답이 유형에 따라
 * 갈라져, 부르는 쪽이 문제 유형을 먼저 알아야 한다.
 */
@RestController
@RequestMapping("/api/v1/problems")
class QuizController(private val quizSubmissionService: QuizSubmissionService) {

    /** 번호와 slug 를 둘 다 받는다 — 문제 상세와 같은 규칙이다 (#204, #600). */
    @AuthenticatedApi
    @PostMapping("/{key}/quiz")
    fun submit(
        @PathVariable key: String,
        @Valid @RequestBody request: QuizSubmitRequest,
        principal: AuthPrincipal,
    ): QuizSubmitResponse = quizSubmissionService.submit(key, principal.userId, request)
}
