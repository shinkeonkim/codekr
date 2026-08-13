package codekr.api.affiliation.repository

import codekr.api.affiliation.entity.Affiliation
import codekr.api.affiliation.entity.AffiliationDomain
import org.springframework.data.jpa.repository.JpaRepository

/** 소속과 도메인 조회 (#397). */
interface AffiliationRepository : JpaRepository<Affiliation, Long> {
    fun findByDeletedAtIsNullOrderByNameAsc(): List<Affiliation>
    fun findByIdAndDeletedAtIsNull(id: Long): Affiliation?
    fun existsByNameAndDeletedAtIsNull(name: String): Boolean
}

interface AffiliationDomainRepository : JpaRepository<AffiliationDomain, Long> {
    fun findByAffiliationIdOrderByDomainAsc(affiliationId: Long): List<AffiliationDomain>
    fun findByDomain(domain: String): AffiliationDomain?
    fun deleteByAffiliationId(affiliationId: Long)
}
