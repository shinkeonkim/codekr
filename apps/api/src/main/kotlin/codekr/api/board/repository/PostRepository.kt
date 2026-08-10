package codekr.api.board.repository

import codekr.api.board.entity.Board
import codekr.api.board.entity.Post
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface PostRepository : JpaRepository<Post, Long> {

    fun findByIdAndDeletedAtIsNull(id: Long): Post?

    fun findByBoardAndDeletedAtIsNullOrderByIdDesc(board: Board, pageable: Pageable): Page<Post>

    fun findByDeletedAtIsNullOrderByIdDesc(pageable: Pageable): Page<Post>

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
