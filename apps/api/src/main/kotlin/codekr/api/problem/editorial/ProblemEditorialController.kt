package codekr.api.problem.editorial

import codekr.api.auth.security.AuthPrincipal
import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.config.security.AuthenticatedApi
import codekr.api.problem.service.ProblemService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 모범 답안 (#719).
 *
 * **문제 상세에 얹지 않고 길을 따로 낸다.** 상세 응답에 조건부 필드로 넣으면 언젠가
 * 그 조건이 어긋나고, 어긋난 것을 아무도 모른다 — 히든 테스트케이스와 정답 코드를
 * 공개 DTO 에 **담을 자리조차 만들지 않는** 것과 같은 규칙이다.
 */
@RestController
@RequestMapping("/api/v1/problems/{slug}/editorial")
class ProblemEditorialController(
    private val editorialService: ProblemEditorialService,
    private val problemService: ProblemService,
) {

    /**
     * 볼 자격이 없으면 **404** 다. 403 이 아니다 — "있지만 못 본다" 도 정보다.
     *
     * 로그인 없이 열 수 있게 두지 않는다. 공개 API 로 두면 자격이 없다는 응답을
     * 누구나 받아 볼 수 있고, 그 응답들의 차이가 곧 목록이 된다.
     */
    @AuthenticatedApi
    @GetMapping
    fun view(@PathVariable slug: String, principal: AuthPrincipal): EditorialResponse {
        val problem = problemService.requirePublished(slug)
        val editorial = editorialService.forUser(problem.id, principal.userId)
            ?: throw ApiException(ErrorCode.EDITORIAL_NOT_FOUND)
        return EditorialResponse.of(editorial)
    }
}

data class EditorialResponse(
    val body: String,
    val referenceAnswer: String?,
    val referenceLabel: String?,
) {
    companion object {
        fun of(editorial: ProblemEditorial) = EditorialResponse(
            body = editorial.body,
            referenceAnswer = editorial.referenceAnswer,
            referenceLabel = editorial.referenceLabel,
        )
    }
}
