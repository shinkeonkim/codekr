package codekr.api.problem.vote

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface DifficultyVoteRepository : JpaRepository<DifficultyVote, DifficultyVoteId> {

    fun findByProblemIdAndUserId(problemId: Long, userId: Long): DifficultyVote?

    /** 이 문제에 모인 표. **집계는 코드에서 한다** — 중앙값을 SQL 로 쓰면 읽기 어렵다. */
    @Query("SELECT v.level FROM DifficultyVote v WHERE v.problemId = :problemId ORDER BY v.level")
    fun levelsOf(@Param("problemId") problemId: Long): List<Int>
}
