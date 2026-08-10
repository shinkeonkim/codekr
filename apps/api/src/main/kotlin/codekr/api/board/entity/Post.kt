package codekr.api.board.entity

import codekr.api.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** 게시글 (#137). */
@Entity
@Table(name = "posts")
class Post(
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var board: Board,

    @Column(name = "author_id", nullable = false)
    val authorId: Long,

    @Column(nullable = false, length = 200)
    var title: String,

    /**
     * 마크다운 원문.
     *
     * **저장 시점에 HTML 로 바꾸지 않는다.** 그러면 렌더링 규칙을 고칠 때 이미 쌓인 글을
     * 전부 다시 만들어야 하고, 그 사이의 글은 옛 규칙으로 남는다.
     */
    @Column(nullable = false)
    var body: String,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null
        protected set

    fun edit(title: String, body: String) {
        this.title = title
        this.body = body
    }

    fun delete() {
        deletedAt = Instant.now()
    }
}
