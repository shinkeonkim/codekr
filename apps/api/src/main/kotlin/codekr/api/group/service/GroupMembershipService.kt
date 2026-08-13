package codekr.api.group.service

import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.group.dto.GroupInvitePreview
import codekr.api.group.entity.GROUP_MEMBER_LIMIT
import codekr.api.group.entity.Group
import codekr.api.group.entity.GroupMember
import codekr.api.group.repository.GroupMemberRepository
import codekr.api.group.repository.GroupRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 들어오고 나가는 일 (#401).
 *
 * **초대 링크가 기본이고, 공개 가입은 방장이 켠다** (기획서 5절). 처음부터 공개면
 * 스팸 가입이 온다.
 */
@Service
@Transactional
class GroupMembershipService(
    private val groups: GroupRepository,
    private val members: GroupMemberRepository,
    private val groupService: GroupService,
) {

    /** 초대 링크가 가리키는 그룹. **가입 전에** 무엇에 들어가는지 보여 준다. */
    @Transactional(readOnly = true)
    fun preview(token: String, viewerId: Long?): GroupInvitePreview {
        val group = byToken(token)
        return GroupInvitePreview(
            id = group.id,
            name = group.name,
            description = group.description,
            memberCount = members.countByGroupId(group.id),
            member = viewerId != null && members.existsByGroupIdAndUserId(group.id, viewerId),
        )
    }

    fun joinByInvite(token: String, userId: Long): Long = join(byToken(token), userId)

    /**
     * 공개 가입.
     *
     * **꺼져 있으면 링크를 아는 사람만 들어온다.** 여기서 404 가 아니라 403 인 이유:
     * 그룹이 있다는 것은 이미 아는 상태에서 부르는 길이다.
     */
    fun joinOpen(groupId: Long, userId: Long): Long {
        val group = groupService.find(groupId)
        if (!group.openJoin) throw ApiException(ErrorCode.FORBIDDEN, "초대를 받아야 들어올 수 있습니다.")
        return join(group, userId)
    }

    /**
     * 나간다.
     *
     * **방장은 넘기고 나간다.** 방장이 그냥 나가면 그룹이 잠긴다 — 이름도 못 고치고
     * 초대 링크도 못 뽑는 그룹이 남는다.
     */
    fun leave(groupId: Long, userId: Long) {
        val group = groupService.find(groupId)
        if (group.ownerId == userId) {
            throw ApiException(
                ErrorCode.VALIDATION_ERROR,
                "방장은 먼저 방장을 넘기거나 그룹을 해산해야 합니다.",
            )
        }
        members.findByGroupIdAndUserId(groupId, userId)?.let(members::delete)
    }

    /** 내보낸다. 방장만 할 수 있고, **자기 자신은 내보낼 수 없다** — 그것은 해산이다. */
    fun kick(groupId: Long, ownerId: Long, targetUserId: Long) {
        val group = groupService.ownedBy(groupId, ownerId)
        if (group.ownerId == targetUserId) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "방장은 스스로를 내보낼 수 없습니다.")
        }
        members.findByGroupIdAndUserId(groupId, targetUserId)?.let(members::delete)
    }

    /** 방장을 넘긴다. **받는 사람이 멤버여야 한다** — 밖의 사람에게 넘기면 그룹이 잠긴다. */
    fun transferOwner(groupId: Long, ownerId: Long, targetUserId: Long) {
        val group = groupService.ownedBy(groupId, ownerId)
        if (!members.existsByGroupIdAndUserId(groupId, targetUserId)) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "그룹 멤버에게만 넘길 수 있습니다.")
        }
        group.ownerId = targetUserId
    }

    /** 초대 링크를 새로 뽑는다. **옛 링크는 그 자리에서 죽는다.** */
    fun rotateInvite(groupId: Long, ownerId: Long): String =
        groupService.ownedBy(groupId, ownerId).also(Group::rotateInvite).inviteToken

    private fun byToken(token: String): Group =
        groups.findByInviteTokenAndDeletedAtIsNull(token)
            ?: throw ApiException(ErrorCode.RESOURCE_NOT_FOUND, "초대 링크가 유효하지 않습니다.")

    private fun join(group: Group, userId: Long): Long {
        // 두 번 눌러도 두 번 들어가지 않는다.
        if (members.existsByGroupIdAndUserId(group.id, userId)) return group.id

        // **인원 상한이 없으면 "전체 랭킹" 을 흉내 내는 그룹이 생긴다** (기획서 5절).
        if (members.countByGroupId(group.id) >= GROUP_MEMBER_LIMIT) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "인원이 가득 찼습니다. (최대 $GROUP_MEMBER_LIMIT 명)")
        }
        members.save(GroupMember(group.id, userId))
        return group.id
    }
}
