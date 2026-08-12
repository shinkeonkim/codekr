package codekr.api.audit.repository

import codekr.api.audit.entity.AdminAuditLog
import codekr.api.audit.entity.AuditTargetType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 관리 기록 조회 (#225).
 *
 * **고치거나 지우는 메서드를 두지 않는다** — `JpaRepository` 가 주는 것 중 쓰는 것은
 * 저장과 조회뿐이다.
 */
interface AdminAuditLogRepository : JpaRepository<AdminAuditLog, Long> {

    /** "이 회원에게 무슨 일이 있었나". */
    fun findByTargetTypeAndTargetIdOrderByIdDesc(
        targetType: AuditTargetType,
        targetId: Long,
        pageable: Pageable,
    ): Page<AdminAuditLog>

    /** "이 어드민이 무엇을 했나". 권한을 가진 사람이 늘면 이쪽이 먼저 궁금해진다. */
    fun findByActorIdOrderByIdDesc(actorId: Long, pageable: Pageable): Page<AdminAuditLog>

    fun findAllByOrderByIdDesc(pageable: Pageable): Page<AdminAuditLog>
}
