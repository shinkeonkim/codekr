package codekr.api.contest.board

import codekr.api.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** 대회 공지 (#147). 운영자가 올리고 참가자 전원이 본다. */
@Entity
@Table(name = "contest_notices")
class ContestNotice(
    @Column(name = "contest_id", nullable = false)
    val contestId: Long,

    @Column(nullable = false, length = 200)
    var title: String,

    @Column(nullable = false)
    var body: String,

    @Column(name = "created_by")
    val createdBy: Long? = null,
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
