package codekr.api.problem.repository

import codekr.api.problem.entity.Problem
import org.springframework.data.jpa.repository.JpaRepository

interface ProblemRepository : JpaRepository<Problem, Long> {

    fun findBySlugAndDeletedAtIsNull(slug: String): Problem?

    fun findByIdAndDeletedAtIsNull(id: Long): Problem?

    /** 있는지만 본다 (#219). 문제 본문·테스트케이스까지 읽을 이유가 없는 자리에서 쓴다. */
    fun existsByIdAndDeletedAtIsNull(id: Long): Boolean

    fun existsBySlugAndDeletedAtIsNull(slug: String): Boolean
}
