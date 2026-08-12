package codekr.api.audit.controller

import codekr.api.audit.dto.AdminAuditLogResponse
import codekr.api.audit.entity.AuditTargetType
import codekr.api.audit.repository.AdminAuditLogRepository
import codekr.api.common.dto.PageResponse
import codekr.api.config.security.AdminApi
import codekr.api.user.entity.UserRole
import codekr.api.user.repository.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 관리 기록 조회 (#225).
 *
 * **최고 관리자만 본다.** 회원 목록은 어드민까지 열려 있지만(#223) 이 기록은 성격이
 * 다르다 — **어드민끼리 서로를 보는 것**이고, 여기 남는 행위도 전부 최고 관리자의 것이다.
 *
 * 고치거나 지우는 경로가 없다. 감사 기록의 뜻이 거기에 있다.
 */
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
class AdminAuditController(
    private val repository: AdminAuditLogRepository,
    private val userRepository: UserRepository,
) {

    @AdminApi(UserRole.SUPERUSER)
    @GetMapping
    fun search(
        /** "이 회원에게 무슨 일이 있었나". */
        @RequestParam(required = false) targetUserId: Long?,
        /** "이 어드민이 무엇을 했나". 권한을 가진 사람이 늘면 이쪽이 먼저 궁금해진다. */
        @RequestParam(required = false) actorId: Long?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<AdminAuditLogResponse> {
        val pageable = PageRequest.of(maxOf(page, 0), size.coerceIn(1, 100))
        val logs = when {
            targetUserId != null ->
                repository.findByTargetTypeAndTargetIdOrderByIdDesc(AuditTargetType.USER, targetUserId, pageable)
            actorId != null -> repository.findByActorIdOrderByIdDesc(actorId, pageable)
            else -> repository.findAllByOrderByIdDesc(pageable)
        }

        // 어드민 닉네임을 한 번에 읽는다. 줄마다 읽으면 스무 번 더 나간다.
        val actors = userRepository.findAllById(logs.content.map { it.actorId }.distinct())
            .associate { it.id to it.nickname }

        return PageResponse.from(logs.map { AdminAuditLogResponse.of(it, actors[it.actorId]) })
    }
}
