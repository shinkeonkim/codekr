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
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 공개 문제집을 목록에서 내린다 (#208).
 *
 * **지우지 않는다.** 남이 만든 것을 지우는 것과 목록에서 빼는 것은 다르고, 되돌릴 수
 * 있어야 한다 — 비공개로 되돌리면 주인은 그대로 갖고 있는다.
 */
@RestController
@RequestMapping("/api/v1/admin/collections/{id}")
class AdminCollectionController(private val service: AdminCollectionService) {

    @PostMapping("/takedown")
    @AdminApi(UserRole.ADMIN)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun takedown(
        @PathVariable id: Long,
        @RequestParam reason: String?,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ) = service.takedown(principal.userId, id, reason)
}

@Service
class AdminCollectionService(
    private val collections: ProblemCollectionRepository,
    private val users: UserRepository,
    private val auditService: AdminAuditService,
    private val notificationService: NotificationService,
) {

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
