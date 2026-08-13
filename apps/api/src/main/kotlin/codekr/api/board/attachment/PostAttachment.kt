package codekr.api.board.attachment

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

/**
 * 글에 붙는 이미지 (#389).
 *
 * **표가 필요한 이유는 정리 때문이다.** 오브젝트 스토리지에는 "누가 언제 올렸는지" 가
 * 없다 — 올려 놓고 글을 안 쓰면 주인 없는 파일이 남는데, 그것을 치우려면 나이를 알아야 한다.
 */
@Entity
@Table(name = "post_attachments")
class PostAttachment(
    @Column(nullable = false)
    val uploaderId: Long,

    @Column(nullable = false, length = 200)
    val storageKey: String,

    @Column(nullable = false)
    val byteSize: Int,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(nullable = false)
    val createdAt: Instant = Instant.now()
}

interface PostAttachmentRepository : JpaRepository<PostAttachment, Long> {

    fun findByStorageKey(storageKey: String): PostAttachment?

    /** 그 사람이 최근에 올린 개수. 올리는 속도를 제한하는 데 쓴다. */
    fun countByUploaderIdAndCreatedAtAfter(uploaderId: Long, after: Instant): Long

    /**
     * 오래됐고 **아무 글·댓글에도 안 쓰인** 것 (#389, #46).
     *
     * 본문을 파싱하지 않고 `LIKE` 로 찾는 이유: 파싱은 마크다운 규칙이 바뀔 때마다 함께
     * 바뀌어야 하고, **틀리면 쓰고 있는 이미지를 지운다.** 키가 본문에 글자로 들어 있는지만
     * 보면 규칙이 바뀌어도 **안 지우는 쪽으로** 틀린다.
     */
    @Query(
        """
        SELECT a FROM PostAttachment a
        WHERE a.createdAt < :before
          AND NOT EXISTS (SELECT 1 FROM Post p WHERE p.body LIKE CONCAT('%', a.storageKey, '%'))
          AND NOT EXISTS (SELECT 1 FROM Comment c WHERE c.body LIKE CONCAT('%', a.storageKey, '%'))
        """,
    )
    fun findOrphans(@Param("before") before: Instant): List<PostAttachment>
}
