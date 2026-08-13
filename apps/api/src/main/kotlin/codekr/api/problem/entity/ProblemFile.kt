package codekr.api.problem.entity

import codekr.api.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 여러 파일을 완성하는 문제의 파일 하나 (#457).
 *
 * **파일 목록은 런타임마다다.** 같은 문제라도 자바는 `Main.java`·`Helper.java` 이고
 * 파이썬은 `main.py`·`helper.py` 다 — 시작 코드가 런타임마다인 것과 같은 이유다.
 */
@Entity
@Table(name = "problem_files")
class ProblemFile(

    @Column(name = "problem_id", nullable = false)
    val problemId: Long,

    @Column(name = "runtime_id", nullable = false, length = 40)
    val runtimeId: String,

    @Column(nullable = false)
    var seq: Int,

    @Column(nullable = false, length = 60)
    var name: String,

    @Column(nullable = false, columnDefinition = "text")
    var template: String = "",

    /**
     * 고칠 수 있는 파일인가.
     *
     * 거짓이면 **제출에 실리지 않는다** — 서버가 시작 코드를 그대로 쓴다. 그래야
     * "이 인터페이스는 건드리지 말고 구현만 하라" 가 화면의 약속이 아니라 서버의 규칙이 된다.
     */
    @Column(nullable = false)
    var editable: Boolean = true,

) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}
