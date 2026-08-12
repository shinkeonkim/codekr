package codekr.api.tag.admin

import codekr.api.user.entity.UserRole
import codekr.api.config.security.AdminApi
import codekr.api.tag.dto.ProblemTagResponse
import codekr.api.tag.dto.TagResponse
import codekr.api.tag.service.TagService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 태그 관리 (#232). 태그를 다는 것은 어드민만 한다. */
@RestController
@RequestMapping("/api/v1/admin/tags")
class AdminTagController(private val tagService: TagService) {

    @AdminApi(UserRole.PROBLEM_SETTER)
    @PostMapping
    fun create(@Valid @RequestBody request: TagCreateRequest): TagResponse =
        tagService.create(request.slug, request.name, request.description)

    @AdminApi(UserRole.PROBLEM_SETTER)
    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody request: TagUpdateRequest): TagResponse =
        tagService.update(id, request.name, request.description)
}

/** 문제에 태그를 다는 자리 (#232). 문제 경로 아래라 PROBLEM_SETTER 권한이 적용된다 (#103). */
@RestController
@RequestMapping("/api/v1/admin/problems")
class AdminProblemTagController(private val tagService: TagService) {

    @AdminApi(UserRole.PROBLEM_SETTER)
    @PutMapping("/{id}/tags")
    fun replace(@PathVariable id: Long, @Valid @RequestBody request: ProblemTagsRequest): List<ProblemTagResponse> =
        tagService.replaceTagsOf(id, request.tagIds)
}

data class TagCreateRequest(
    /** 주소에 들어가므로 소문자·숫자·붙임표만 받는다. 나중에 바꿀 수 없다. */
    @field:Pattern(regexp = "[a-z0-9-]{1,60}", message = "태그 주소는 소문자·숫자·붙임표만 쓸 수 있습니다.")
    val slug: String,
    @field:NotBlank(message = "태그 이름은 필수입니다.")
    @field:Size(max = 60, message = "태그 이름이 너무 깁니다.")
    val name: String,
    @field:Size(max = 300, message = "태그 설명이 너무 깁니다.")
    val description: String? = null,
)

data class TagUpdateRequest(
    @field:NotBlank(message = "태그 이름은 필수입니다.")
    @field:Size(max = 60, message = "태그 이름이 너무 깁니다.")
    val name: String,
    @field:Size(max = 300, message = "태그 설명이 너무 깁니다.")
    val description: String? = null,
)

/** 통째로 바꾼다. 비우면 태그가 없는 문제가 된다. */
data class ProblemTagsRequest(val tagIds: List<Long> = emptyList())
