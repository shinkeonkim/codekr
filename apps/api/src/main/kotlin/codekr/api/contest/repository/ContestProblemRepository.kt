package codekr.api.contest.repository

import codekr.api.contest.entity.ContestProblem
import codekr.api.contest.entity.ContestProblemId
import org.springframework.data.jpa.repository.JpaRepository

interface ContestProblemRepository : JpaRepository<ContestProblem, ContestProblemId> {

    fun findByIdContestIdOrderBySeqAsc(contestId: Long): List<ContestProblem>

    fun deleteByIdContestId(contestId: Long)

    /** 이 문제가 배정된 대회들 (#139). 대회 중이면 질문을 막는다. */
    fun findByIdProblemId(problemId: Long): List<ContestProblem>
}
