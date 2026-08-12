package codekr.api.tag.repository

import codekr.api.tag.entity.ProblemTag
import codekr.api.tag.entity.ProblemTagId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ProblemTagRepository : JpaRepository<ProblemTag, ProblemTagId> {

    fun findByIdProblemId(problemId: Long): List<ProblemTag>

    fun deleteByIdProblemId(problemId: Long)

    /**
     * 태그별 **공개된** 문제 수 (#232).
     *
     * 공개되지 않은 문제를 세면 화면이 "12문제" 라고 해 놓고 목록에는 3개만 나온다.
     * 지운 문제도 마찬가지다 — 세는 조건이 거르는 조건과 같아야 한다.
     */
    @Query(
        """
        SELECT pt.id.tagId, count(pt)
        FROM ProblemTag pt
        JOIN Problem p ON p.id = pt.id.problemId
        WHERE p.deletedAt IS NULL AND p.published = true
        GROUP BY pt.id.tagId
        """,
    )
    fun countPublishedByTag(): List<Array<Any>>
}
