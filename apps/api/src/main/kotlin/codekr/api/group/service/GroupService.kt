package codekr.api.group.service

import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.group.dto.GroupDetail
import codekr.api.group.dto.GroupMemberView
import codekr.api.group.dto.GroupRequest
import codekr.api.group.dto.GroupSummary
import codekr.api.group.entity.GROUP_MEMBER_LIMIT
import codekr.api.group.entity.Group
import codekr.api.group.entity.GroupMember
import codekr.api.group.repository.GroupMemberRepository
import codekr.api.group.repository.GroupRepository
import codekr.api.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 그룹 만들기와 보기 (#401, #240 6단계).
 *
 * **로그인한 누구나 만든다** (기획서 5절). 스터디는 아무 때나 생긴다 — 어드민을
 * 거치게 하면 그 순간 만들어지지 않는다.
 */
@Service
@Transactional(readOnly = true)
class GroupService(
    private val groups: GroupRepository,
    private val members: GroupMemberRepository,
    private val users: UserRepository,
) {

    /** 내가 든 그룹. **방장인 것도 여기 함께 온다** — 두 목록으로 나눌 이유가 없다. */
    fun mine(userId: Long): List<GroupSummary> =
        members.findByUserIdOrderByJoinedAtAsc(userId)
            .mapNotNull { groups.findByIdAndDeletedAtIsNull(it.groupId) }
            .map { GroupSummary(it.id, it.name, members.countByGroupId(it.id), it.ownerId == userId) }

    @Transactional
    fun create(userId: Long, request: GroupRequest): GroupDetail {
        val group = groups.save(
            Group(request.name.trim(), userId, request.description.trim())
                .also { it.openJoin = request.openJoin },
        )
        // **방장도 멤버다.** 아니면 "내 그룹" 목록과 인원 수가 방장만 다르게 센다.
        members.save(GroupMember(group.id, userId))
        return detail(group.id, userId)
    }

    /**
     * 그룹 상세.
     *
     * **누가 들어 있는지는 멤버만 본다.** 이름과 인원까지는 초대 링크가 보여 주지만
     * (가입 전에 무엇에 들어가는지 알아야 한다), 명단은 그 안의 일이다.
     */
    fun detail(groupId: Long, viewerId: Long?): GroupDetail {
        val group = find(groupId)
        val isMember = viewerId != null && members.existsByGroupIdAndUserId(groupId, viewerId)
        val isOwner = group.ownerId == viewerId
        if (!isMember) throw ApiException(ErrorCode.FORBIDDEN, "그룹의 멤버만 볼 수 있습니다.")

        return GroupDetail(
            id = group.id,
            name = group.name,
            description = group.description,
            openJoin = group.openJoin,
            memberCount = members.countByGroupId(groupId),
            memberLimit = GROUP_MEMBER_LIMIT,
            owner = isOwner,
            member = true,
            inviteToken = group.inviteToken.takeIf { isOwner },
            members = memberViews(group),
        )
    }

    @Transactional
    fun update(groupId: Long, userId: Long, request: GroupRequest): GroupDetail {
        val group = ownedBy(groupId, userId)
        group.name = request.name.trim()
        group.description = request.description.trim()
        group.openJoin = request.openJoin
        return detail(groupId, userId)
    }

    /**
     * 해산한다.
     *
     * **행을 지우지 않는다** (ADR-0007). 그룹 랭킹(#402)이 이 id 를 가리키고 있고,
     * 지우면 "어느 그룹이었는지" 를 아무도 모르게 된다.
     */
    @Transactional
    fun delete(groupId: Long, userId: Long) {
        ownedBy(groupId, userId).delete()
    }

    fun find(groupId: Long): Group =
        groups.findByIdAndDeletedAtIsNull(groupId)
            ?: throw ApiException(ErrorCode.RESOURCE_NOT_FOUND, "그룹을 찾을 수 없습니다.")

    fun ownedBy(groupId: Long, userId: Long): Group =
        find(groupId).also {
            if (it.ownerId != userId) throw ApiException(ErrorCode.FORBIDDEN, "방장만 할 수 있습니다.")
        }

    private fun memberViews(group: Group): List<GroupMemberView> =
        members.findByGroupIdOrderByJoinedAtAsc(group.id).mapNotNull { member ->
            val user = users.findById(member.userId).orElse(null) ?: return@mapNotNull null
            GroupMemberView(
                userId = user.id,
                nickname = user.nickname,
                handle = user.handle,
                owner = user.id == group.ownerId,
                joinedAt = member.joinedAt,
            )
        }
}
