package codekr.api.contest.admin

import codekr.api.audit.entity.AdminAction
import codekr.api.audit.service.AdminAuditService
import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.contest.entity.ContestPhase
import codekr.api.contest.entity.ContestRegistrationId
import codekr.api.contest.entity.ContestRegistrationStatus
import codekr.api.contest.repository.ContestRegistrationRepository
import codekr.api.contest.repository.ContestRepository
import codekr.api.notification.entity.NotificationCategory
import codekr.api.notification.service.NotificationService
import codekr.api.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 참가 신청을 보고 받거나 거절한다 (#466).
 *
 * **시작한 뒤에는 승인하지 않는다.** 늦게 승인된 사람은 남들보다 시간이 적고, 그것은
 * 곧 "순위가 공정하지 않다" 가 된다 — 운영자가 시작 전에 처리해야 하고, 화면이 그
 * 사실을 분명히 적는다.
 */
@Service
@Transactional(readOnly = true)
class ContestApprovalService(
    private val contests: ContestRepository,
    private val registrations: ContestRegistrationRepository,
    private val users: UserRepository,
    private val notificationService: NotificationService,
    private val auditService: AdminAuditService,
) {

    /** 승인 대기 목록. 오래 기다린 사람이 위다. */
    fun pending(contestId: Long): List<PendingApplicant> {
        val contest = require(contestId)
        return registrations
            .findByIdContestIdAndStatusOrderByRegisteredAtAsc(contest.id, ContestRegistrationStatus.PENDING)
            .mapNotNull { registration ->
                users.findById(registration.id.userId).orElse(null)?.let { user ->
                    PendingApplicant(user.id, user.nickname, user.handle, registration.registeredAt)
                }
            }
    }

    @Transactional
    fun approve(actorId: Long, contestId: Long, userId: Long) {
        val contest = require(contestId)
        /*
            **시작한 뒤에는 승인하지 않는다.**

            늦게 들어온 사람은 남들보다 시간이 적다. 그것을 열어 두면 순위표가 무엇을
            재는지 흐려진다 — 열지 않는 편이 운영자에게도 설명하기 쉽다.
        */
        val phase = contest.phaseAt(Instant.now())
        if (phase != ContestPhase.SCHEDULED && phase != ContestPhase.DRAFT) {
            throw ApiException(
                ErrorCode.VALIDATION_ERROR,
                "대회가 시작한 뒤에는 승인할 수 없습니다. 늦게 승인하면 그 사람만 시간이 적습니다.",
            )
        }

        val registration = registrations.findById(ContestRegistrationId(contestId, userId))
            .orElseThrow { ApiException(ErrorCode.RESOURCE_NOT_FOUND, "참가 신청을 찾을 수 없습니다.") }
        if (registration.approved) return

        registration.approve()
        notify(userId, contest.slug, "참가가 승인되었습니다", "이제 대회에서 문제를 풀 수 있습니다.")
        record(actorId, AdminAction.CONTEST_APPROVAL, userId, "${contest.slug} 참가 승인", null)
    }

    /**
     * 거절한다 — **행을 지운다.**
     *
     * 상태로 남기면 다시 신청할 수 없고, "왜 거절됐는지" 는 관리 기록(#225)이 답한다.
     * 그래서 **사유가 필수다** — 지운 뒤에는 그것이 유일한 설명이다.
     */
    @Transactional
    fun reject(actorId: Long, contestId: Long, userId: Long, reason: String) {
        if (reason.isBlank()) throw ApiException(ErrorCode.VALIDATION_ERROR, "사유를 입력해 주세요.")
        val contest = require(contestId)
        val registration = registrations.findById(ContestRegistrationId(contestId, userId))
            .orElseThrow { ApiException(ErrorCode.RESOURCE_NOT_FOUND, "참가 신청을 찾을 수 없습니다.") }
        if (registration.approved) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "이미 승인된 참가자입니다.")
        }

        registrations.delete(registration)
        // **말없이 사라지면 다시 신청할지도 모른다.** 사유를 그대로 전한다.
        notify(userId, contest.slug, "참가 신청이 받아들여지지 않았습니다", "사유: $reason")
        record(actorId, AdminAction.CONTEST_APPROVAL, userId, "${contest.slug} 참가 거절", reason)
    }

    private fun require(contestId: Long) = contests.findByIdAndDeletedAtIsNull(contestId)
        ?: throw ApiException(ErrorCode.CONTEST_NOT_FOUND)

    private fun notify(userId: Long, slug: String, title: String, body: String) =
        notificationService.notify(
            userId = userId,
            category = NotificationCategory.SYSTEM,
            title = title,
            body = body,
            link = "/contests/$slug",
        )

    private fun record(actorId: Long, action: AdminAction, targetId: Long, detail: String, reason: String?) =
        auditService.record(
            actorId = actorId,
            action = action,
            targetId = targetId,
            targetLabel = users.findById(targetId).map { it.nickname }.orElse(null),
            reason = reason,
            detail = detail,
        )
}

/** 승인 대기 중인 신청자 한 줄 (#466). 판단에 필요한 것까지만. */
data class PendingApplicant(
    val userId: Long,
    val nickname: String,
    val handle: String,
    val appliedAt: Instant,
)
