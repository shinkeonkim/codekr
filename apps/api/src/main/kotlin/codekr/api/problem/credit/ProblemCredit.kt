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
}
