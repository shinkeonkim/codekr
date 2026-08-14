package codekr.api.problem.repository

import codekr.api.problem.entity.ProblemRedisSpec
import org.springframework.data.jpa.repository.JpaRepository

interface ProblemRedisSpecRepository : JpaRepository<ProblemRedisSpec, Long>
