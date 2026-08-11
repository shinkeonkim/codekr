package codekr.api.board.comment

import org.springframework.data.jpa.repository.JpaRepository

interface CommentRepository : JpaRepository<Comment, Long> {

    /**
     * 한 글의 댓글을 **한 번에** 읽는다 (#138).
     *
     * 트리를 만들려고 부모마다 질의하면 댓글 수만큼 쿼리가 나간다.
     * 전부 읽어 메모리에서 잇는 편이 훨씬 싸다 — 글 하나의 댓글은 많아야 수백 개다.
     */
    fun findByPostIdOrderByIdAsc(postId: Long): List<Comment>

    /** 목록에 보여줄 댓글 수. 삭제된 것은 세지 않는다. */
    fun countByPostIdAndDeletedAtIsNull(postId: Long): Long
}
