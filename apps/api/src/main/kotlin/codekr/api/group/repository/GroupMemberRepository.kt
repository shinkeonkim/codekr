package codekr.api.group.repository

import codekr.api.group.entity.GroupMember
import org.springframework.data.jpa.repository.JpaRepository

interface GroupMemberRepository : JpaRepository<GroupMember, Long> {
    fun findByGroupIdOrderByJoinedAtAsc(groupId: Long): List<GroupMember>
    fun findByUserIdOrderByJoinedAtAsc(userId: Long): List<GroupMember>
    fun findByGroupIdAndUserId(groupId: Long, userId: Long): GroupMember?
    fun existsByGroupIdAndUserId(groupId: Long, userId: Long): Boolean
    fun countByGroupId(groupId: Long): Int
}
