package codekr.api.group.admin

import codekr.api.audit.entity.AdminAction
import codekr.api.audit.service.AdminAuditService
import codekr.api.common.dto.PageResponse
import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.group.repository.GroupMemberRepository
import codekr.api.group.repository.GroupRepository
import codekr.api.notification.entity.NotificationCategory
import codekr.api.notification.service.NotificationService
import codekr.api.user.repository.UserRepository
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class AdminGroupService(
    private val groups: GroupRepository,
    private val members: GroupMemberRepository,
    private val users: UserRepository,
    private val notificationService: NotificationService,
    private val auditService: AdminAuditService,
    private val jdbcClient: JdbcClient,
) {

    /**
     * 목록.
     *
     * **인원까지 한 번에 센다.** 그룹마다 세면 20건짜리 목록이 21번 조회한다 —
     * 문제 목록이 통계를 모아 읽는 것과 같은 이유다.
     */
    fun list(keyword: String?, page: Int, size: Int): PageResponse<AdminGroupRow> {
        val where = if (keyword.isNullOrBlank()) "" else "AND g.name ILIKE '%' || :q || '%'"
        val total = jdbcClient.sql("SELECT count(*) FROM groups g WHERE g.deleted_at IS NULL $where")
            .param("q", keyword)
            .query(Long::class.java)
            .single()

        val rows = jdbcClient.sql(
            """
            SELECT g.id, g.name, g.description, g.open_join, g.created_at,
                   u.nickname AS owner_nickname,
                   (SELECT count(*) FROM group_members m WHERE m.group_id = g.id) AS member_count
            FROM groups g
            JOIN users u ON u.id = g.owner_id
            WHERE g.deleted_at IS NULL $where
            ORDER BY g.created_at DESC
            LIMIT :size OFFSET :offset
            """,
        )
            .param("q", keyword)
            .param("size", size)
            .param("offset", page * size)
            .query { rs, _ ->
                AdminGroupRow(
                    id = rs.getLong("id"),
                    name = rs.getString("name"),
                    description = rs.getString("description"),
                    ownerNickname = rs.getString("owner_nickname"),
                    memberCount = rs.getInt("member_count"),
                    openJoin = rs.getBoolean("open_join"),
                    createdAt = rs.getTimestamp("created_at").toInstant(),
                )
            }
            .list()

        return PageResponse(rows, page, size, total, ((total + size - 1) / size).toInt())
    }

    /**
     * 해산한다.
     *
     * **말없이 사라지면 고칠 수도 없다** (#208 이 문제집에서 정한 것과 같다). 멤버는
     * 그룹이 없어진 것만 보고 이유를 알 길이 없다 — **전원에게** 사유와 함께 알린다.
     * 방장만 알리지 않는 이유: 그룹은 방장의 것이 아니라 그 안 사람들의 것이다.
     */
    @Transactional
    fun takedown(actorId: Long, groupId: Long, reason: String) {
        if (reason.isBlank()) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "사유를 입력해 주세요.")
        }
        val group = groups.findByIdAndDeletedAtIsNull(groupId)
            ?: throw ApiException(ErrorCode.RESOURCE_NOT_FOUND, "그룹을 찾을 수 없습니다.")

        // 행은 지우지 않는다 (ADR-0007). 초대 링크는 이것으로 함께 죽는다 (#401).
        group.delete()

        members.findByGroupIdOrderByJoinedAtAsc(groupId).forEach { member ->
            notificationService.notify(
                userId = member.userId,
                category = NotificationCategory.SYSTEM,
                title = "'${group.name}' 그룹이 내려갔습니다",
                body = "사유: $reason",
                link = "/groups",
            )
        }

        auditService.record(
            actorId = actorId,
            action = AdminAction.GROUP_TAKEDOWN,
            targetId = group.ownerId,
            targetLabel = users.findById(group.ownerId).map { it.nickname }.orElse(null),
            reason = reason,
            detail = "${group.name} (${members.countByGroupId(groupId)}명)",
        )
    }
}
