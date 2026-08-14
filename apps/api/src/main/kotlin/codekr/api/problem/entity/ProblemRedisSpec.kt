package codekr.api.problem.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * Redis 문제의 스펙 (#455).
 *
 * **정답을 결과가 아니라 상태로 본다.** 제출이 명령의 연속이면 마지막 명령의 출력은
 * 문제가 묻는 것의 일부일 뿐이다 — `LPUSH` 가 돌려주는 길이는 정답과 상관이 없다.
 * 그래서 정답 명령을 돌린 인스턴스와 제출을 돌린 인스턴스에서 **같은 확인 명령**을
 * 돌려 그 출력을 견준다.
 */
@Entity
@Table(name = "problem_redis_specs")
class ProblemRedisSpec(
    @Id
    @Column(name = "problem_id")
    val problemId: Long,

    /** 시작 상태를 만드는 명령. 관리자로 넣는다. 문제가 소유한다. */
    @Column(name = "seed_commands")
    var seedCommands: String? = null,

    @Column(name = "answer_commands", nullable = false)
    var answerCommands: String,

    /**
     * 끝난 뒤의 상태를 읽는 명령. **SQL 과 달리 선택이 아니다** — 견줄 결과 집합이
     * 없으므로 이것이 없으면 무엇을 정답으로 볼지가 없다.
     */
    @Column(name = "verify_commands", nullable = false)
    var verifyCommands: String,

    /**
     * 줄 순서를 무시할지. **기본은 무시하지 않는다.**
     *
     * SQL 의 행 순서와 반대다 — 정렬 집합·리스트에서 순서는 자료의 일부이고, 그것을
     * 무시하면 정렬이 틀린 답이 통과한다.
     */
    @Column(name = "ignore_order", nullable = false)
    var ignoreOrder: Boolean = false,
)
