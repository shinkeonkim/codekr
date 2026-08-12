package codekr.api.problem.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal

/**
 * 저장된 문제 통계 (#205).
 *
 * **정렬을 위해서만 있다.** 화면에 보여 주는 값은 여전히 `ProblemStatsRepository` 가
 * 세어 온다 — 한 페이지분을 세는 것은 싸고, 그쪽이 언제나 맞다.
 *
 * 여기 값이 어긋나면 목록의 **차례**가 틀리지 신뢰할 수 없는 숫자가 보이지는 않는다.
 * 그래도 어긋난 것을 알아낼 수단은 둔다 (`ProblemStatsSyncRepository.findDrift`).
 *
 * 쓰기는 전부 `ProblemStatsSyncRepository` 가 SQL 로 한다. 이 엔티티는 **읽기 전용**이다 —
 * `acceptance` 는 DB 가 계산하는 칸이라 JPA 로 쓸 수 없다.
 */
@Entity
@Table(name = "problem_stats")
class ProblemStat(
    @Id
    @Column(name = "problem_id")
    val problemId: Long = 0,

    @Column(nullable = false)
    val submitters: Int = 0,

    @Column(nullable = false)
    val solvers: Int = 0,

    /** 제출자가 없으면 `null` 이다 — **0/0 은 정답률이 없는 것이지 0% 가 아니다.** */
    @Column(insertable = false, updatable = false)
    val acceptance: BigDecimal? = null,
)
