package codekr.api.collection.admin

import codekr.api.audit.entity.AdminAction
import codekr.api.audit.service.AdminAuditService
import codekr.api.auth.security.AuthPrincipal
import codekr.api.collection.entity.CollectionVisibility
import codekr.api.collection.repository.ProblemCollectionRepository
import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.config.security.AdminApi
import codekr.api.notification.entity.NotificationCategory
import codekr.api.notification.service.NotificationService
import codekr.api.user.entity.UserRole
import codekr.api.user.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import codekr.api.common.dto.PageResponse
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 어드민 문제집 관리 (#208, #393).
 *
 * **남에게 보여지는 것만 본다.** `PRIVATE` 는 목록에도 상세에도 나오지 않는다 —
 * 남이 혼자 쓰는 목록을 들여다보는 것은 다른 문제이고, 이 화면이 푸는 문제가 아니다.
 * 남에게 보여지려면 `UNLISTED`(링크) 이상이어야 하므로 **"비공개로 두고 링크를 뿌리는"
 * 우회로도 없다** — 링크를 뿌리려면 `UNLISTED` 가 되어야 한다.
 *
 * 역할은 `ADMIN` 하나다. 새 역할을 만들지 않았다 — 지금 여섯인데 쓸 화면이 없는 것이
 * 이미 있었다(#103).
 */
@RestController
@RequestMapping("/api/v1/admin/collections")
class AdminCollectionController(private val service: AdminCollectionService) {

    /** 남에게 보여지는 문제집 목록. 범위로 좁힐 수 있다. */
    @GetMapping
    @AdminApi(UserRole.ADMIN)
    fun list(
        @RequestParam(required = false) visibility: CollectionVisibility?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ) = service.list(visibility, PageRequest.of(maxOf(page, 0), size.coerceIn(1, 100)))

    /**
     * 담긴 문제까지 본다.
     *
     * **무엇이 문제인지는 내용을 봐야 안다** — 이름만 보고 내리면 잘못 내린다.
     */
    @GetMapping("/{id}")
    @AdminApi(UserRole.ADMIN)
    fun detail(@PathVariable id: Long) = service.detail(id)

    @PostMapping("/{id}/takedown")
    @AdminApi(UserRole.ADMIN)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun takedown(
        @PathVariable id: Long,
        @RequestParam reason: String?,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ) = service.takedown(principal.userId, id, reason)
}

/** 목록 한 줄 (#393). 무엇을 볼지 정하는 데 필요한 것만 싣는다. */
data class AdminCollectionRow(
    val id: Long,
    val name: String,
    val visibility: CollectionVisibility,
    val visibilityLabel: String,
    val ownerNickname: String,
    val problemCount: Int,
    val createdAt: java.time.Instant,
)

/** 상세 — 담긴 문제까지. */
data class AdminCollectionDetail(
    val id: Long,
    val name: String,
    val description: String,
    val visibility: CollectionVisibility,
    val visibilityLabel: String,
    val ownerNickname: String,
    val createdAt: java.time.Instant,
    val problems: List<AdminCollectionProblem>,
)

data class AdminCollectionProblem(val problemId: Long, val slug: String, val title: String)

@Service
class AdminCollectionService(
    private val collections: ProblemCollectionRepository,
    private val users: UserRepository,
    private val auditService: AdminAuditService,
    private val notificationService: NotificationService,
    private val jdbcClient: JdbcClient,
) {

    /**
     * 남에게 보여지는 문제집만 (#393).
     *
     * **`PRIVATE` 를 빼는 것이 조건의 전부다.** 범위 인자는 그 안에서 더 좁히는 것이라,
     * `PRIVATE` 를 넣어 부르면 아무것도 나오지 않는다 — 막는 것이 아니라 없는 것이다.
     */
    fun list(visibility: CollectionVisibility?, pageable: Pageable): PageResponse<AdminCollectionRow> {
        val scope = visibility?.takeIf { it != CollectionVisibility.PRIVATE }?.name
        val rows = jdbcClient.sql(
            """
            SELECT c.id, c.name, c.visibility, u.nickname AS owner_nickname, c.created_at,
                   (SELECT count(*) FROM problem_collection_items i WHERE i.collection_id = c.id) AS problem_count
            FROM problem_collections c
            JOIN users u ON u.id = c.owner_id
            WHERE c.deleted_at IS NULL
              AND c.visibility <> 'PRIVATE'
              AND (CAST(:scope AS text) IS NULL OR c.visibility = CAST(:scope AS text))
            ORDER BY c.created_at DESC
            LIMIT :limit OFFSET :offset
            """,
        )
            .param("scope", scope)
            .param("limit", pageable.pageSize)
            .param("offset", pageable.offset)
            .query { rs, _ ->
                val value = CollectionVisibility.valueOf(rs.getString("visibility"))
                AdminCollectionRow(
                    id = rs.getLong("id"),
                    name = rs.getString("name"),
                    visibility = value,
                    visibilityLabel = value.label,
                    ownerNickname = rs.getString("owner_nickname"),
                    problemCount = rs.getInt("problem_count"),
                    createdAt = rs.getTimestamp("created_at").toInstant(),
                )
            }
            .list()

        val total = jdbcClient.sql(
            """
            SELECT count(*) FROM problem_collections c
            WHERE c.deleted_at IS NULL AND c.visibility <> 'PRIVATE'
              AND (CAST(:scope AS text) IS NULL OR c.visibility = CAST(:scope AS text))
            """,
        ).param("scope", scope).query(Long::class.java).single()

        return PageResponse.from(org.springframework.data.domain.PageImpl(rows, pageable, total))
    }

    /** 담긴 문제까지. **`PRIVATE` 는 여기서도 없는 것으로 다룬다.** */
    fun detail(id: Long): AdminCollectionDetail {
        val collection = collections.findByIdAndDeletedAtIsNull(id)
            ?.takeIf { it.visibility != CollectionVisibility.PRIVATE }
            ?: throw ApiException(ErrorCode.COLLECTION_NOT_FOUND)

        val problems = jdbcClient.sql(
            """
            SELECT p.id, p.slug, p.title
            FROM problem_collection_items i
            JOIN problems p ON p.id = i.problem_id
            WHERE i.collection_id = :id
            ORDER BY i.seq
            """,
        )
            .param("id", id)
            .query { rs, _ -> AdminCollectionProblem(rs.getLong("id"), rs.getString("slug"), rs.getString("title")) }
            .list()

        return AdminCollectionDetail(
            id = collection.id,
            name = collection.name,
            description = collection.description,
            visibility = collection.visibility,
            visibilityLabel = collection.visibility.label,
            ownerNickname = users.findById(collection.ownerId).map { it.nickname }.orElse("(알 수 없음)"),
            createdAt = collection.createdAt,
            problems = problems,
        )
    }

    @Transactional
    fun takedown(actorId: Long, collectionId: Long, reason: String?) {
        val collection = collections.findByIdAndDeletedAtIsNull(collectionId)
            ?: throw ApiException(ErrorCode.COLLECTION_NOT_FOUND)
        if (collection.visibility != CollectionVisibility.PUBLIC) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "공개 문제집이 아닙니다.")
        }

        collection.visibility = CollectionVisibility.PRIVATE

        /*
            **말없이 사라지면 고칠 수도 없다** (#208).

            주인은 자기 문제집이 목록에서 빠진 것을 알 길이 없다 — 비공개로 바뀐 것만
            보인다. 왜 내렸는지를 함께 알린다.
        */
        notificationService.notify(
            userId = collection.ownerId,
            category = NotificationCategory.SYSTEM,
            title = "문제집이 공개 목록에서 내려갔습니다",
            body = reason?.let { "사유: $it" } ?: "내용을 확인한 뒤 다시 공개할 수 있습니다.",
            link = "/collections/${collection.id}",
        )

        auditService.record(
            actorId = actorId,
            action = AdminAction.COLLECTION_TAKEDOWN,
            targetId = collection.ownerId,
            targetLabel = users.findById(collection.ownerId).map { it.nickname }.orElse(null),
            reason = reason,
            detail = collection.name,
        )
    }
}
