package codekr.api.user.service

import codekr.api.audit.entity.AdminAction
import codekr.api.audit.service.AdminAuditService
import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.user.dto.UserProfileResponse
import codekr.api.user.entity.UserRole
import codekr.api.user.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 역할 부여/회수 (#103). 최고 관리자만 부를 수 있다 (SecurityConfig).
 *
 * 역할을 주고 뺏는 일은 되돌리기 어렵고, 그 권한을 가진 사람이 많아지면 누가 무엇을
 * 줬는지 추적이 안 된다. 그래서 SUPERUSER 로 좁힌다.
 */
@Service
@Transactional
class UserRoleService(
    private val auditService: AdminAuditService,private val userRepository: UserRepository) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun replaceRoles(userId: Long, roles: Set<UserRole>, actorId: Long): Set<UserRole> {
        val user = userRepository.findById(userId).orElseThrow { ApiException(ErrorCode.USER_NOT_FOUND) }

        /*
         * 자기 자신의 최고 관리자 권한은 뺄 수 없다.
         *
         * 마지막 최고 관리자가 실수로 자기 권한을 빼면 아무도 역할을 되돌릴 수 없다 —
         * DB 를 직접 고치는 것 말고는 복구 경로가 없어진다.
         */
        if (user.id == actorId && UserRole.SUPERUSER !in roles && user.has(UserRole.SUPERUSER)) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "자기 자신의 최고 관리자 권한은 뺄 수 없습니다.")
        }

        // USER 는 항상 남긴다. 빼면 로그인은 되는데 아무것도 못 하는 계정이 된다.
        val next = roles + UserRole.USER
        UserRole.entries.forEach { if (it in next) user.grant(it) else user.revoke(it) }

        log.info("역할 변경: userId={} roles={} actorId={}", userId, next, actorId)
        /*
            관리 기록에 남긴다 (#225).

            이 서비스의 주석이 이미 걱정하던 것이다 — "그 권한을 가진 사람이 많아지면
            누가 무엇을 줬는지 추적이 안 된다". **권한을 좁히는 것은 추적의 대용품**이었고,
            회원 관리 화면(#223)이 생긴 지금은 이 일이 훨씬 자주 일어난다.
        */
        auditService.record(
            actorId = actorId,
            action = AdminAction.ROLE_CHANGE,
            targetId = user.id,
            targetLabel = user.nickname,
            detail = next.sorted().joinToString(", "),
        )
        return user.roles
    }

    @Transactional(readOnly = true)
    fun rolesOf(nickname: String): Set<UserRole> =
        (userRepository.findByNickname(nickname) ?: throw ApiException(ErrorCode.USER_NOT_FOUND)).roles
}
