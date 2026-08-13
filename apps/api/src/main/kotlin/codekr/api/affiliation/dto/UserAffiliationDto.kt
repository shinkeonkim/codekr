package codekr.api.affiliation.dto

import codekr.api.affiliation.entity.AffiliationKind

data class MyAffiliationsResponse(
    val attached: List<AttachedAffiliation>,
    /**
     * 지금 붙일 수 있는 소속.
     *
     * **확인한 주소의 도메인이 가리키는 것 중 아직 안 붙인 것**이다. 비어 있으면
     * 화면은 "학교·회사 메일을 확인해 주세요" 를 말한다.
     */
    val attachable: List<AttachableAffiliation>,
)

data class AttachedAffiliation(
    val affiliationId: Long,
    val name: String,
    val kind: AffiliationKind,
    val kindLabel: String,
    /** 어느 주소로 붙었는지. 그 주소를 떼면 이 소속도 떨어진다. */
    val email: String,
)

data class AttachableAffiliation(
    val affiliationId: Long,
    val name: String,
    val kindLabel: String,
    val email: String,
)
