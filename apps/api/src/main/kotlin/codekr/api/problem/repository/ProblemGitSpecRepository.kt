package codekr.api.problem.repository

import codekr.api.problem.entity.ProblemGitSpec
import org.springframework.data.jpa.repository.JpaRepository

/** Git 문제의 스펙 (#654). 문제 하나에 하나다. */
interface ProblemGitSpecRepository : JpaRepository<ProblemGitSpec, Long>
