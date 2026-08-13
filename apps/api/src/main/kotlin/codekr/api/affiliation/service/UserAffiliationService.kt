package codekr.api.affiliation.service

import codekr.api.affiliation.dto.AttachableAffiliation
import codekr.api.affiliation.dto.AttachedAffiliation
import codekr.api.affiliation.dto.MyAffiliationsResponse
import codekr.api.affiliation.entity.Affiliation
import codekr.api.affiliation.entity.UserAffiliation
import codekr.api.affiliation.repository.AffiliationDomainRepository
import codekr.api.affiliation.repository.AffiliationRepository
import codekr.api.affiliation.repository.UserAffiliationRepository
import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import codekr.api.user.email.repository.UserEmailRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserAffiliationService(
    private val userAffiliations: UserAffiliationRepository,
    private val affiliations: AffiliationRepository,
    private val domains: AffiliationDomainRepository,
    private val userEmails: UserEmailRepository,
) {

    fun mine(userId: Long): MyAffiliationsResponse {
        val emails = userEmails.findByUserIdOrderByIdAsc(userId)
        val attached = userAffiliations.findByUserIdOrderByIdAsc(userId).mapNotNull { link ->
            val affiliation = affiliations.findById(link.affiliationId).orElse(null) ?: return@mapNotNull null
            AttachedAffiliation(
                affiliationId = affiliation.id,
                name = affiliation.name,
                kind = affiliation.kind,
                kindLabel = affiliation.kind.label,
                email = emails.firstOrNull { it.id == link.userEmailId }?.email ?: "(지운 주소)",
            )
        }

        val attachedIds = attached.map { it.affiliationId }.toSet()
        val attachable = candidates(userId)
            .filterNot { it.first.id in attachedIds }
            .map { (affiliation, email) ->
                AttachableAffiliation(affiliation.id, affiliation.name, affiliation.kind.label, email)
            }

        return MyAffiliationsResponse(attached, attachable)
    }

    /**
     * 붙인다.
     *
     * **화면이 보내는 값을 믿지 않는다.** 소속 id 만 받고, 그 소속에 붙을 자격이
     * 있는지는 여기서 확인한 주소로 다시 찾는다 — 화면을 안 거친 요청에도 같은 규칙이
     * 걸려야 한다.
     */
    @Transactional
    fun attach(userId: Long, affiliationId: Long) {
        if (userAffiliations.existsByUserIdAndAffiliationId(userId, affiliationId)) return

        val (affiliation, emailId) = candidates(userId).firstOrNull { it.first.id == affiliationId }
            ?.let { it.first to it.third }
            ?: throw ApiException(
                ErrorCode.VALIDATION_ERROR,
                "그 소속의 메일 주소를 먼저 확인해 주세요.",
            )

        userAffiliations.save(UserAffiliation(userId, affiliation.id, emailId))
    }

    /**
     * 뗀다.
     *
     * **사용자가 뗀다** (기획서 4절). 떼면 그 소속 랭킹에서 사라진다 — "어제까지 1등이던
     * 사람이 오늘 없다" 가 생기지만, **"지금 그곳 사람들의 순위" 라는 뜻은 유지된다.**
     */
    @Transactional
    fun detach(userId: Long, affiliationId: Long) {
        val link = userAffiliations.findByUserIdOrderByIdAsc(userId)
            .firstOrNull { it.affiliationId == affiliationId }
            ?: return
        userAffiliations.delete(link)
    }

    /**
     * 확인한 주소가 가리키는 소속들.
     *
     * 내려간 소속(`deletedAt`)은 나오지 않는다 — 도메인이 함께 떼어지므로 자연히 그렇다(#397).
     */
    private fun candidates(userId: Long): List<Triple<Affiliation, String, Long>> =
        userEmails.findByUserIdOrderByIdAsc(userId).mapNotNull { email ->
            val domain = email.email.substringAfterLast('@')
            val link = domains.findByDomain(domain) ?: return@mapNotNull null
            val affiliation = affiliations.findByIdAndDeletedAtIsNull(link.affiliationId) ?: return@mapNotNull null
            Triple(affiliation, email.email, email.id)
        }
}
