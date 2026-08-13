package codekr.api.problem.repository

import codekr.api.problem.entity.ProblemTestcaseGroup
import org.springframework.data.jpa.repository.JpaRepository

interface ProblemTestcaseGroupRepository : JpaRepository<ProblemTestcaseGroup, Long> {
    fun findByProblemIdOrderByGroupNo(problemId: Long): List<ProblemTestcaseGroup>
    fun deleteByProblemId(problemId: Long)
}
