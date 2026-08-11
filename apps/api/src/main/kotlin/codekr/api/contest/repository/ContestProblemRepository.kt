package codekr.api.contest.repository

import codekr.api.contest.entity.ContestProblem
import codekr.api.contest.entity.ContestProblemId
import org.springframework.data.jpa.repository.JpaRepository

interface ContestProblemRepository : JpaRepository<ContestProblem, ContestProblemId> {

    fun findByIdContestIdOrderBySeqAsc(contestId: Long): List<ContestProblem>

    fun deleteByIdContestId(contestId: Long)
}
