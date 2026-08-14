package codekr.api.problem.repository

import codekr.api.problem.entity.ProblemFile
import org.springframework.data.jpa.repository.JpaRepository

interface ProblemFileRepository : JpaRepository<ProblemFile, Long> {
    fun findByProblemIdOrderBySeq(problemId: Long): List<ProblemFile>
    fun findByProblemIdAndRuntimeIdOrderBySeq(problemId: Long, runtimeId: String): List<ProblemFile>
    fun deleteByProblemId(problemId: Long)
}
