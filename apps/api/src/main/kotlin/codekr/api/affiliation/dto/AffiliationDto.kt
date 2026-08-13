package codekr.api.affiliation.dto

import codekr.api.affiliation.entity.AffiliationKind
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class AffiliationRequest(
    @field:NotBlank(message = "이름을 입력해 주세요.")
    @field:Size(max = 100)
    val name: String = "",
    val kind: AffiliationKind = AffiliationKind.SCHOOL,
)

data class DomainRequest(
    @field:NotBlank(message = "도메인을 입력해 주세요.")
    val domain: String = "",
)

data class AffiliationResponse(
    val id: Long,
    val name: String,
    val kind: AffiliationKind,
    val kindLabel: String,
    val domains: List<DomainResponse>,
)

data class DomainResponse(val id: Long, val domain: String)
