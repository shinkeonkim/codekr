package codekr.api.problem.editorial

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 모범 답안 (#719).
 *
 * **채점에 쓰는 정답과 물리적으로 다른 자리다.** Git·SQL·Redis·MongoDB 는 기대값이
 * `answerCommands`·`answerSql` 에서 만들어지므로, 그 칸을 겸하면 **읽기 좋게 다듬는
 * 것이 채점 기준을 바꾸는 일**이 된다.
 *
 * `Problem.solutionSourceCode`(#39) 도 쓰지 않는다. 그쪽은 판정을 안 바꾸지만
 * `verificationSignature()` 에 들어 있어서, **오타 하나를 고치면 그 문제가 "검증되지
 * 않은 문제" 가 된다.**
 */
@Entity
@Table(name = "problem_editorials")
class ProblemEditorial(
    @Id
    @Column(name = "problem_id")
    val problemId: Long,

    /** 풀이 설명. 마크다운이다. */
    @Column(nullable = false)
    var body: String,

    /**
     * 참고 답안. **선택 사항이고, 아무도 실행하지 않는다.**
     *
     * 유형마다 담기는 것이 다르다 — 코드일 수도, git 명령일 수도, 쿼리일 수도 있다.
     * 실행할 것이면 유형별 스펙 표로 가야 한다.
     */
    @Column(name = "reference_answer")
    var referenceAnswer: String? = null,

    /** 참고 답안이 무엇인지 (`python:3.12`, `git 명령`, `SQL`). */
    @Column(name = "reference_label", length = 60)
    var referenceLabel: String? = null,
)
