package codekr.api.board.comment

import codekr.api.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** 댓글 (#138). 부모가 없으면 최상위다. */
@Entity
@Table(name = "comments")
class Comment(
    @Column(name = "post_id", nullable = false)
    val postId: Long,

    @Column(name = "parent_id")
    val parentId: Long? = null,

    @Column(name = "author_id", nullable = false)
    val authorId: Long,

    @Column(nullable = false)
    var body: String,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null
        protected set

    val isDeleted: Boolean get() = deletedAt != null

    fun edit(body: String) {
        this.body = body
    }

    /**
     * 소프트 삭제.
     *
     * **본문을 지우지 않는다.** 되돌릴 수 있어야 하고, 잘못 지운 것을 복구할 방법이
     * 없으면 삭제가 되돌릴 수 없는 선택이 된다. 화면에는 내려보내지 않는다.
     */
    fun delete() {
        deletedAt = Instant.now()
    }
}
