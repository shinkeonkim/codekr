package codekr.api.problem.credit

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.io.Serializable
import java.time.Instant

/**
 * 기여의 종류 (#236).
 *
 * **검수는 정답 코드 검증(#39)과 다르다.** 그것은 기계가 도는 것이고, 이것은 사람이
 * 읽어 본 것이다 — 검증 버튼을 누른 사람을 검수자로 자동 기록하지 않는다.
 */
enum class CreditRole(val label: String) {
    SETTER("출제"),
    REVIEWER("검수"),

    /**
     * 오류를 찾아 문제를 고치게 한 사람 (#478).
     *
     * **어드민이 정하지 않는다.** 신고가 받아들여질 때 붙는다 — 그래서 편집 화면의
     * 출제·검수 목록과 달리, 문제를 저장해도 지워지지 않아야 한다.
     */
    CONTRIBUTOR("기여"),
}

@Embeddable
data class ProblemCreditId(
    @Column(name = "problem_id") val problemId: Long = 0,
    @Column(name = "user_id") val userId: Long = 0,
    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 16)
    val role: CreditRole = CreditRole.SETTER,
) : Serializable

/** 누가 이 문제에 무엇으로 기여했는가 (#236). */
@Entity
@Table(name = "problem_credits")
class ProblemCredit(
    @EmbeddedId
    val id: ProblemCreditId,
) {
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
}

interface ProblemCreditRepository : JpaRepository<ProblemCredit, ProblemCreditId> {

    fun findByIdProblemId(problemId: Long): List<ProblemCredit>

    fun deleteByIdProblemId(problemId: Long)

    /**
     * 어드민이 고르는 역할만 지운다 (#478).
     *
     * 기여자(#478)는 신고가 받아들여질 때 붙는 것이라, 문제를 저장할 때마다 통째로
     * 지우면 **고친 사람의 이름이 다음 편집에서 사라진다.**
     */
    fun deleteByIdProblemIdAndIdRoleIn(problemId: Long, roles: Collection<CreditRole>)
}
