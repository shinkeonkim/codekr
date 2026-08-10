package codekr.api.rejudge.repository

import codekr.api.rejudge.entity.RejudgeBatch
import org.springframework.data.jpa.repository.JpaRepository

interface RejudgeBatchRepository : JpaRepository<RejudgeBatch, Long> {
    fun findFirstByProblemIdOrderByIdDesc(problemId: Long): RejudgeBatch?
}
