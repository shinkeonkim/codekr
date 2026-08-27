package codekr.api.problem.repository

import codekr.api.problem.entity.ProblemMutant
import codekr.api.problem.entity.ProblemMutationSpec
import org.springframework.data.jpa.repository.JpaRepository

/** 테스트 작성 문제의 설정 (#652). 문제 하나에 하나다. */
interface ProblemMutationSpecRepository : JpaRepository<ProblemMutationSpec, Long>

/**
 * 버그를 심은 구현들 (#652).
 *
 * **통째로 갈아 끼운다** — 번호가 바뀌면 그것은 다른 구현이고, 무엇이 무엇의 개정인지
 * 짐작하면 판정 순서가 어긋난다. 보기(#650)·파일(#457)이 같은 판단을 했다.
 */
interface ProblemMutantRepository : JpaRepository<ProblemMutant, Long> {
    fun findByProblemIdOrderBySeqAsc(problemId: Long): List<ProblemMutant>
    fun deleteByProblemId(problemId: Long)
}
