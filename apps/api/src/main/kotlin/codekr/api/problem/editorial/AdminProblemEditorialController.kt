package codekr.api.problem.editorial

import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.config.security.AdminApi
import codekr.api.user.entity.UserRole
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 모범 답안 쓰기 (#719). 문제를 내는 사람이 함께 쓴다. */
@AdminApi(UserRole.PROBLEM_SETTER)
@RestController
@RequestMapping("/api/v1/admin/problems/{id}/editorial")
class AdminProblemEditorialController(
    private val editorialService: ProblemEditorialService,
) {

    @GetMapping
    fun view(@PathVariable id: Long): EditorialResponse =
        editorialService.forAdmin(id)?.let(EditorialResponse::of)
            ?: throw ApiException(ErrorCode.EDITORIAL_NOT_FOUND)

    @PutMapping
    fun save(@PathVariable id: Long, @Valid @RequestBody request: EditorialRequest): EditorialResponse =
        EditorialResponse.of(
            editorialService.save(id, request.body, request.referenceAnswer, request.referenceLabel),
        )

    @DeleteMapping
    fun delete(@PathVariable id: Long) = editorialService.delete(id)
}

data class EditorialRequest(
    @field:NotBlank(message = "풀이 설명을 적어 주세요.")
    @field:Size(max = MAX_BODY, message = "풀이 설명이 너무 깁니다.")
    val body: String,
    @field:Size(max = MAX_ANSWER, message = "참고 답안이 너무 깁니다.")
    val referenceAnswer: String? = null,
    @field:Size(max = 60)
    val referenceLabel: String? = null,
) {
    companion object {
        const val MAX_BODY = 20000
        const val MAX_ANSWER = 20000
    }
}
