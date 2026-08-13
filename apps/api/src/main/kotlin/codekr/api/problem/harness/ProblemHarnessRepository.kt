package codekr.api.problem.harness

import org.springframework.data.jpa.repository.JpaRepository

interface ProblemHarnessRepository : JpaRepository<ProblemHarness, Long> {
    fun findByProblemId(problemId: Long): List<ProblemHarness>
    fun findByProblemIdAndRuntimeId(problemId: Long, runtimeId: String): ProblemHarness?
    fun deleteByProblemId(problemId: Long)
}
