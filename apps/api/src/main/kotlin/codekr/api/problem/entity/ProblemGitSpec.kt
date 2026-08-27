package codekr.api.problem.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * Git 문제의 스펙 (#654).
 *
 * **Redis(#455)와 모양이 같다** — 정답을 결과가 아니라 **끝난 뒤의 상태**로 본다.
 * 담기는 것이 git 명령이라는 것과, 하네스가 해야 하는 일이 더 많다는 것만 다르다.
 */
@Entity
@Table(name = "problem_git_specs")
class ProblemGitSpec(
    @Id
    @Column(name = "problem_id")
    val problemId: Long,

    /** 시작 저장소를 만드는 명령. 없으면 빈 저장소에서 시작한다. */
    @Column(name = "seed_commands")
    var seedCommands: String? = null,

    @Column(name = "answer_commands", nullable = false)
    var answerCommands: String,

    /**
     * 끝난 뒤를 읽는 명령. **선택이 아니다.**
     *
     * 커밋 해시를 그대로 찍는 것은 권하지 않는다 — 메시지 한 글자만 달라도 해시가
     * 달라져 **같은 결과에 이른 다른 풀이가 틀린 답**이 된다.
     */
    @Column(name = "verify_commands", nullable = false)
    var verifyCommands: String,
)
