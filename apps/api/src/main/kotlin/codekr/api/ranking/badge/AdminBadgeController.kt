package codekr.api.ranking.badge

import codekr.api.config.security.AdminApi
import codekr.api.config.security.PublicApi
import codekr.api.user.entity.UserRole
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 뱃지 정의 관리 (#201).
 *
 * **지우는 길이 없다.** 이미 받은 사람이 있는 뱃지를 지우면 그 사람의 프로필에서
 * 이름 없는 코드가 된다 — 대신 `visible` 을 끈다.
 */
@RestController
@RequestMapping("/api/v1")
class AdminBadgeController(private val service: AdminBadgeService) {

    /** 목록. 화면이 "무슨 뱃지가 있는지" 를 여기서 안다 — 코드를 읽던 자리를 대신한다. */
    @PublicApi
    @GetMapping("/badges")
    fun findVisible(): List<BadgeDefinition> = service.visible()

    @AdminApi(UserRole.ADMIN)
    @GetMapping("/admin/badges")
    fun findAll(): List<BadgeDefinition> = service.all()

    @AdminApi(UserRole.ADMIN)
    @PostMapping("/admin/badges")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: BadgeCreateRequest) = service.create(request)

    /**
     * 고친다. **코드는 바꿀 수 없다** — `user_badges` 에 박히는 값이라 한 번 준 뒤에는
     * 가리키는 대상이 달라진다.
     */
    @AdminApi(UserRole.ADMIN)
    @PutMapping("/admin/badges/{code}")
    fun update(@PathVariable code: String, @Valid @RequestBody request: BadgeUpdateRequest) =
        service.update(code, request)
}

data class BadgeCreateRequest(
    @field:Pattern(regexp = "^[A-Z0-9_]+$", message = "코드는 대문자·숫자·밑줄만 쓸 수 있습니다.")
    @field:Size(min = 2, max = 60)
    val code: String,
    @field:NotBlank @field:Size(max = 60) val label: String,
    @field:NotBlank @field:Size(max = 200) val description: String,
    @field:NotBlank @field:Size(max = 60) val ruleKey: String,
    val visible: Boolean = true,
    val sortOrder: Int = 0,
)

data class BadgeUpdateRequest(
    @field:NotBlank @field:Size(max = 60) val label: String,
    @field:NotBlank @field:Size(max = 200) val description: String,
    val visible: Boolean = true,
    val sortOrder: Int = 0,
)

@Service
class AdminBadgeService(
    private val jdbcClient: JdbcClient,
    private val catalog: BadgeCatalog,
) {

    fun all(): List<BadgeDefinition> = catalog.all()

    fun visible(): List<BadgeDefinition> = catalog.visible()

    @Transactional
    fun create(request: BadgeCreateRequest): BadgeDefinition {
        jdbcClient.sql(
            """
            INSERT INTO badges (code, label, description, visible, sort_order, rule_key)
            VALUES (:code, :label, :description, :visible, :sortOrder, :ruleKey)
            """,
        )
            .param("code", request.code)
            .param("label", request.label)
            .param("description", request.description)
            .param("visible", request.visible)
            .param("sortOrder", request.sortOrder)
            .param("ruleKey", request.ruleKey)
            .update()

        catalog.invalidate()
        return catalog.describeDefinition(request.code)
    }

    /**
     * **문구를 고치면 이미 받은 사람에게도 바뀐다.**
     *
     * 받은 시점의 문구를 남기려면 `user_badges` 에 사본이 필요한데, 그러면 원래
     * 구조("코드만 저장")로 돌아간다. 바뀌는 것을 받아들이고, 화면이 그 사실을 경고한다.
     */
    @Transactional
    fun update(code: String, request: BadgeUpdateRequest): BadgeDefinition {
        jdbcClient.sql(
            """
            UPDATE badges
            SET label = :label, description = :description, visible = :visible,
                sort_order = :sortOrder, updated_at = now()
            WHERE code = :code
            """,
        )
            .param("code", code)
            .param("label", request.label)
            .param("description", request.description)
            .param("visible", request.visible)
            .param("sortOrder", request.sortOrder)
            .update()

        catalog.invalidate()
        return catalog.describeDefinition(code)
    }
}
