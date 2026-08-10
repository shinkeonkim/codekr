package codekr.api.problem.repository

import codekr.api.problem.entity.ProblemSqlSpec
import org.springframework.data.jpa.repository.JpaRepository

interface ProblemSqlSpecRepository : JpaRepository<ProblemSqlSpec, Long>
