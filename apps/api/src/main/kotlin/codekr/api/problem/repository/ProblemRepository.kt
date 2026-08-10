package codekr.api.problem.repository

import codekr.api.problem.entity.Problem
import org.springframework.data.jpa.repository.JpaRepository

interface ProblemRepository : JpaRepository<Problem, Long> {

    fun findBySlugAndDeletedAtIsNull(slug: String): Problem?

    fun findByIdAndDeletedAtIsNull(id: Long): Problem?

    fun existsBySlugAndDeletedAtIsNull(slug: String): Boolean
}
