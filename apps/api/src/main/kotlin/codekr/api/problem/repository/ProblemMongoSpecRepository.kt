package codekr.api.problem.repository

import codekr.api.problem.entity.ProblemMongoSpec
import org.springframework.data.jpa.repository.JpaRepository

interface ProblemMongoSpecRepository : JpaRepository<ProblemMongoSpec, Long>
