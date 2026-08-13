package codekr.api.terms

import codekr.api.common.error.ApiException
import codekr.api.common.error.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

/** 약관과 동의 (#235). */
@Service
class TermsService(
    private val documents: TermDocumentRepository,
    private val agreements: TermAgreementRepository,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun effective(): List<TermDocument> = documents.findEffective(clock.instant())

    @Transactional(readOnly = true)
    fun findOne(id: Long): TermDocument =
        documents.findById(id).orElseThrow { ApiException(ErrorCode.TERMS_NOT_FOUND) }

    /**
     * 가입할 때 받은 동의를 남긴다.
     *
     * **필수를 다 받지 못하면 가입이 되지 않는다.** 동의 없이 만들어진 계정은 나중에
     * "받았다" 고 말할 근거가 없다.
     */
    @Transactional
    fun agreeOnSignup(userId: Long, documentIds: List<Long>) {
        val effective = effective()
        val agreed = documentIds.toSet()

        val missing = effective.filter { it.required && it.id !in agreed }
        if (missing.isNotEmpty()) {
            throw ApiException(
                ErrorCode.VALIDATION_ERROR,
                "필수 약관에 동의해야 합니다: ${missing.joinToString(", ") { it.title }}",
            )
        }
        record(userId, effective.filter { it.id in agreed }.map { it.id })
    }

    /** 개정 뒤에 다시 받는다. */
    @Transactional
    fun agree(userId: Long, documentIds: List<Long>) {
        val effectiveIds = effective().map { it.id }.toSet()
        // 시행 중이 아닌 판에 동의하는 것은 뜻이 없다 — 조용히 거른다.
        record(userId, documentIds.filter { it in effectiveIds })
    }

    /**
     * 지금 다시 받아야 하는 것들.
     *
     * **다시 받아야 하는 개정에만 걸린다** (`reconsent`). 오타를 고칠 때마다 모든 회원을
     * 막으면 개정 자체를 안 하게 된다.
     */
    @Transactional(readOnly = true)
    fun pending(userId: Long): List<TermDocument> {
        val mine = agreements.findByUserId(userId).map { it.documentId }.toSet()
        return effective().filter { (it.required || it.reconsent) && it.id !in mine }
    }

    @Transactional(readOnly = true)
    fun mine(userId: Long): List<Pair<TermDocument, Instant>> {
        val mine = agreements.findByUserId(userId)
        if (mine.isEmpty()) return emptyList()
        val byId = documents.findAllById(mine.map { it.documentId }).associateBy { it.id }
        return mine.mapNotNull { agreement -> byId[agreement.documentId]?.let { it to agreement.agreedAt } }
    }

    private fun record(userId: Long, documentIds: List<Long>) {
        val fresh = documentIds.distinct().filterNot { agreements.existsByUserIdAndDocumentId(userId, it) }
        agreements.saveAll(fresh.map { TermAgreement(userId, it) })
    }
}
