package codekr.api.problem.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * MongoDB 문제의 스펙 (#527).
 *
 * **모양은 Redis(#455)와 같다** — 시드로 시작 상태를 만들고, 정답 스크립트를 돌리고,
 * 확인 스크립트로 끝난 뒤를 읽는다. 표를 따로 둔 이유는 그 안에 담기는 것이 다르기
 * 때문이다: 여기는 `mongosh` 스크립트이고 저기는 redis 명령이다.
 *
 * **"결과 집합" 과 "끝난 뒤의 상태" 를 따로 두지 않는다.** 확인 스크립트가 `find` 를
 * 찍으면 결과 집합이 되고 컬렉션을 세면 상태가 된다 — 한 가지 장치로 둘 다 된다.
 * 유형을 늘리지 않으려는 것이 아니라, 실제로 같은 것이기 때문이다.
 */
@Entity
@Table(name = "problem_mongo_specs")
class ProblemMongoSpec(
    @Id
    @Column(name = "problem_id")
    val problemId: Long,

    /** 시작 상태를 만드는 스크립트. 관리자로 넣는다. 문제가 소유한다. */
    @Column(name = "seed_script")
    var seedScript: String? = null,

    @Column(name = "answer_script", nullable = false)
    var answerScript: String,

    /**
     * 끝난 뒤를 읽는 스크립트. **선택이 아니다** — 이것이 없으면 무엇을 정답으로
     * 볼지가 없다.
     */
    @Column(name = "verify_script", nullable = false)
    var verifyScript: String,

    /** 줄 순서를 무시할지. **기본은 무시하지 않는다** — Redis 와 같은 판단이다. */
    @Column(name = "ignore_order", nullable = false)
    var ignoreOrder: Boolean = false,
)
