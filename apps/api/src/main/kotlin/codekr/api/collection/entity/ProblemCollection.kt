package codekr.api.collection.entity

import codekr.api.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.security.SecureRandom
import java.time.Instant
import java.util.HexFormat

/**
 * 문제집 (#87).
 *
 * "DP 입문 10선", "면접 전 마지막 점검" 처럼 문제를 묶어 **직접 커리큘럼을 만든다.**
 */
@Entity
@Table(name = "problem_collections")
class ProblemCollection(
    @Column(name = "owner_id", nullable = false)
    val ownerId: Long,

    @Column(nullable = false, length = 120)
    var name: String,

    @Column(nullable = false)
    var description: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var visibility: CollectionVisibility = CollectionVisibility.PRIVATE,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    /**
     * 링크 공유용 식별자.
     *
     * **id 를 쓰지 않는 이유:** 번호를 바꿔 가며 남의 문제집을 찾을 수 있다.
     * 링크를 아는 사람만 볼 수 있다는 말이 성립하려면 추측할 수 없어야 한다.
     */
    @Column(name = "share_token", nullable = false, length = 32, updatable = false)
    val shareToken: String = newToken()

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null
        protected set

    fun delete() {
        deletedAt = Instant.now()
    }

    fun isVisibleTo(viewerId: Long?): Boolean =
        ownerId == viewerId || visibility == CollectionVisibility.UNLISTED

    private companion object {
        val RANDOM = SecureRandom()

        fun newToken(): String = ByteArray(16)
            .also(RANDOM::nextBytes)
            .let(HexFormat.of()::formatHex)
    }
}
