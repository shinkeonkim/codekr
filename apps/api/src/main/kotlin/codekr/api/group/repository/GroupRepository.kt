package codekr.api.group.repository

import codekr.api.group.entity.Group
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface GroupRepository : JpaRepository<Group, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): Group?

    /** 초대 링크로 찾는다. **내려간 그룹의 링크는 죽는다.** */
    fun findByInviteTokenAndDeletedAtIsNull(inviteToken: String): Group?

    /**
     * 공개 가입을 켜 둔 그룹 (#554).
     *
     * **켜 두고 아무도 못 들어오던 것이 이 조회가 없어서였다.** 들어가는 문(`joinOpen`)은
     * 있었는데 그 문이 어디 있는지 찾을 길이 없었다 — 목록은 내 그룹만 줬다.
     */
    fun findByOpenJoinIsTrueAndDeletedAtIsNullOrderByIdDesc(pageable: Pageable): Page<Group>
}
