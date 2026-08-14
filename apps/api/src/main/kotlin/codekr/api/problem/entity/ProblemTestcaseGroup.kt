package codekr.api.problem.entity

import codekr.api.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 부분 점수 묶음 (#473).
 *
 * **묶음은 대개 제약 조건이다** — "N ≤ 1,000", "N ≤ 100,000", "만점". 그래서 이름을
 * 그대로 보여 주는 것이 힌트가 된다: 어디까지 왔는지 알면 무엇을 고칠지도 안다.
 *
 * **랭킹에는 반영되지 않는다** — 만점만 "풀었다" 로 본다 (#57). 값은 남겨 두므로
 * 나중에 열 수 있다.
 */
@Entity
@Table(name = "problem_testcase_groups")
class ProblemTestcaseGroup(

    @Column(name = "problem_id", nullable = false)
    val problemId: Long,

    @Column(name = "group_no", nullable = false)
    var groupNo: Int,

    /** 이 묶음을 **다 맞혔을 때** 받는 점수. */
    @Column(nullable = false)
    var score: Int,

    @Column(nullable = false, length = 60)
    var label: String = "",

) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}
