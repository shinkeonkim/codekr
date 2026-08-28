package codekr.api.problem.repository

import codekr.api.problem.entity.ProblemRegexSpec
import org.springframework.data.jpa.repository.JpaRepository

/** 정규식 문제의 스펙 (#653). 문제 하나에 하나다. */
interface ProblemRegexSpecRepository : JpaRepository<ProblemRegexSpec, Long>
