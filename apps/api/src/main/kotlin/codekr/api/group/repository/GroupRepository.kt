package codekr.api.group.repository

import codekr.api.group.entity.Group
import org.springframework.data.jpa.repository.JpaRepository

interface GroupRepository : JpaRepository<Group, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): Group?

    /** 초대 링크로 찾는다. **내려간 그룹의 링크는 죽는다.** */
    fun findByInviteTokenAndDeletedAtIsNull(inviteToken: String): Group?
}
