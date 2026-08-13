package codekr.api.group.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 그룹에 든 사람 (#401).
 *
 * **역할 칸이 없다.** 방장은 [Group.ownerId] 한 사람뿐이고, 나머지는 모두 같다 —
 * 부방장을 두면 "누가 누구를 내보낼 수 있나" 가 곧바로 따라오는데, 그것을 정할
 * 근거가 아직 없다.
 */
@Entity
@Table(name = "group_members")
class GroupMember(
    @Column(name = "group_id", nullable = false)
    val groupId: Long,

    @Column(name = "user_id", nullable = false)
    val userId: Long,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(name = "joined_at", nullable = false, updatable = false)
    val joinedAt: Instant = Instant.now()
}
