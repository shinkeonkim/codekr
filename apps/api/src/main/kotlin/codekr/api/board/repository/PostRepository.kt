package codekr.api.board.repository

import codekr.api.board.entity.Board
import codekr.api.board.entity.Post
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PostRepository : JpaRepository<Post, Long> {

    /**
     * 여러 글의 댓글 수를 한 번에 센다 (#138).
     *
     * 글마다 세면 목록 한 번에 20번의 질의가 더 나간다.
     */
    @Query(
        """
        SELECT c.postId, count(c)
        FROM Comment c
        WHERE c.postId IN :postIds AND c.deletedAt IS NULL
        GROUP BY c.postId
        """,
    )
    fun countCommentsByPostIds(@Param("postIds") postIds: Collection<Long>): List<Array<Any>>

    fun findByIdAndDeletedAtIsNull(id: Long): Post?

    fun findByBoardAndDeletedAtIsNullOrderByIdDesc(board: Board, pageable: Pageable): Page<Post>

    fun findByDeletedAtIsNullOrderByIdDesc(pageable: Pageable): Page<Post>

    /** 문제에 붙은 질문 (#139). */
    fun findByProblemIdAndDeletedAtIsNullOrderByIdDesc(problemId: Long, pageable: Pageable): Page<Post>

    fun countByProblemIdAndDeletedAtIsNull(problemId: Long): Long

    fun findByBoardAndTitleContainingIgnoreCaseAndDeletedAtIsNullOrderByIdDesc(
        board: Board,
        title: String,
        pageable: Pageable,
    ): Page<Post>

    fun findByTitleContainingIgnoreCaseAndDeletedAtIsNullOrderByIdDesc(
        title: String,
        pageable: Pageable,
    ): Page<Post>
}
