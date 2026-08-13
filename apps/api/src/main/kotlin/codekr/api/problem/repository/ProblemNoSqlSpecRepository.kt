package codekr.api.problem.repository

import codekr.api.problem.entity.ProblemNoSqlSpec
import org.springframework.data.jpa.repository.JpaRepository

interface ProblemNoSqlSpecRepository : JpaRepository<ProblemNoSqlSpec, Long>
