package codekr.api.contest.board

import codekr.api.auth.security.AuthPrincipal
import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.contest.entity.Contest
import codekr.api.contest.entity.ContestRegistrationId
import codekr.api.contest.repository.ContestProblemRepository
import codekr.api.contest.repository.ContestRegistrationRepository
import codekr.api.contest.repository.ContestRepository
import codekr.api.contest.service.ContestService
import codekr.api.notification.entity.NotificationCategory
import codekr.api.notification.service.NotificationService
import codekr.api.user.entity.UserRole
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 대회 공지와 질의 (#147). */
@Service
@Transactional(readOnly = true)
class ContestBoardService(
    private val contestRepository: ContestRepository,
    private val registrationRepository: ContestRegistrationRepository,
    private val contestService: codekr.api.contest.service.ContestService,
    private val contestProblemRepository: ContestProblemRepository,
    private val noticeRepository: ContestNoticeRepository,
    private val questionRepository: ContestQuestionRepository,
    private val notificationService: NotificationService,
) {

    fun notices(slug: String): List<NoticeResponse> =
        noticeRepository.findByContestIdAndDeletedAtIsNullOrderByIdDesc(require(slug).id)
            .map(NoticeResponse::from)

    /**
     * 공지를 올린다 (#147).
     *
     * **참가자 전원에게 알림을 보낸다.** 대회 중의 공지는 읽지 않으면 손해를 보는
     * 정보다 — 화면을 다시 열어 봐야 알 수 있게 두면 안 된다.
     */
    @Transactional
    fun addNotice(slug: String, principal: AuthPrincipal, request: NoticeUpsertRequest): NoticeResponse {
        val contest = requireManaged(slug, principal)
        val notice = noticeRepository.save(
            ContestNotice(contest.id, request.title, request.body, principal.userId),
        )

        notificationService.notifyAll(
            participantsOf(contest.id),
            NotificationCategory.CONTEST,
            "[${contest.title}] ${request.title}",
            body = request.body.take(NOTIFICATION_BODY_LIMIT),
            link = { "/contests/${contest.slug}" },
        )
        return NoticeResponse.from(notice)
    }

    @Transactional
    fun deleteNotice(slug: String, noticeId: Long, principal: AuthPrincipal) {
        requireManaged(slug, principal)
        noticeRepository.findByIdAndDeletedAtIsNull(noticeId)?.delete()
    }

    /**
     * 질의 목록.
     *
     * 운영자는 전부 본다. 참가자는 **자기 질문과 공개 답변**만 본다 —
     * 남의 비공개 답변이 보이면 비공개의 뜻이 없다.
     */
    fun questions(slug: String, principal: AuthPrincipal?): List<QuestionResponse> {
        val contest = require(slug)
        val manager = principal?.let { canManage(it) } ?: false
        val labels = problemLabelsOf(contest.id)

        return questionRepository.findByContestIdOrderByIdDesc(contest.id)
            .filter { it.isVisibleTo(principal?.userId, manager) }
            .map { question ->
                QuestionResponse(
                    id = question.id,
                    problemLabel = question.problemId?.let { labels[it] },
                    body = question.body,
                    answer = question.answer,
                    answerPublic = question.answerPublic,
                    answeredAt = question.answeredAt,
                    createdAt = question.createdAt,
                    mine = question.askerId == principal?.userId,
                )
            }
    }

    @Transactional
    fun ask(slug: String, principal: AuthPrincipal, request: QuestionRequest): QuestionResponse {
        val contest = require(slug)
        // 참가자만 묻는다. 등록하지 않은 사람은 문제도 볼 수 없다 (#61).
        // 승인 전에는 묻지도 못한다 (#466) — 문제를 볼 수 없으므로 물을 것도 없다.
        if (!contestService.isParticipant(contest.id, principal.userId)) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "참가 승인 뒤에 질문할 수 있습니다.")
        }
        questionRepository.save(
            ContestQuestion(contest.id, request.problemId, principal.userId, request.body),
        )
        return questions(slug, principal).first()
    }

    /**
     * 답한다 (#147).
     *
     * 공개 답변은 **전원에게 알린다.** 한 사람에게만 준 정보가 유리하게 작용하면 안 되는
     * 질문이라서 공개하는 것이므로, 알리지 않으면 공개한 의미가 절반만 남는다.
     */
    @Transactional
    fun answer(slug: String, questionId: Long, principal: AuthPrincipal, request: AnswerRequest) {
        val contest = requireManaged(slug, principal)
        val question = questionRepository.findById(questionId)
            .orElseThrow { ApiException(ErrorCode.VALIDATION_ERROR, "질의를 찾을 수 없습니다.") }
        if (question.contestId != contest.id) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "다른 대회의 질의입니다.")
        }

        question.answer(request.answer, request.public, principal.userId)

        val targets = if (request.public) participantsOf(contest.id) else listOf(question.askerId)
        notificationService.notifyAll(
            targets,
            NotificationCategory.CONTEST,
            if (request.public) "[${contest.title}] 질의에 답변이 올라왔습니다" else "[${contest.title}] 질의에 답변이 달렸습니다",
            body = request.answer.take(NOTIFICATION_BODY_LIMIT),
            link = { "/contests/${contest.slug}" },
        )
    }

    private fun participantsOf(contestId: Long): List<Long> =
        registrationRepository.findAll()
            .filter { it.id.contestId == contestId }
            .map { it.id.userId }

    private fun problemLabelsOf(contestId: Long): Map<Long, String> =
        contestProblemRepository.findByIdContestIdOrderBySeqAsc(contestId)
            .associate { it.problemId to ContestService.labelOf(it.seq) }

    private fun require(slug: String): Contest =
        contestRepository.findBySlugAndDeletedAtIsNull(slug) ?: throw ApiException(ErrorCode.CONTEST_NOT_FOUND)

    private fun requireManaged(slug: String, principal: AuthPrincipal): Contest {
        if (!canManage(principal)) throw ApiException(ErrorCode.FORBIDDEN)
        return require(slug)
    }

    private fun canManage(principal: AuthPrincipal): Boolean =
        principal.has(UserRole.CONTEST_MANAGER) ||
            principal.has(UserRole.ADMIN) ||
            principal.has(UserRole.SUPERUSER)

    private companion object {
        /** 알림 본문에 담는 길이. 전문은 대회 화면에서 본다. */
        const val NOTIFICATION_BODY_LIMIT = 200
    }
}
