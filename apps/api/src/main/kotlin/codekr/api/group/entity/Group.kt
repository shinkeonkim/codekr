package codekr.api.group.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.security.SecureRandom
import java.time.Instant
import java.util.HexFormat

/** 인원 상한 (#401, 기획서 5절). 없으면 **"전체 랭킹" 을 흉내 내는 그룹**이 생긴다. */
const val GROUP_MEMBER_LIMIT = 200

/**
 * 그룹 (#401, #240 6단계).
 *
 * **소속과 다른 것이다** (기획서 2절). 누구나 만들고, 사람이 사람을 부른다. 그래서
 * **사칭이 가능하다** — 막을 방법이 없으므로 막지 않고, 대신 화면이 소속과 그룹을
 * 절대 같은 목록에 섞지 않는다. 사칭의 피해는 "섞여 보일 때" 생긴다.
 */
@Entity
@Table(name = "groups")
class Group(
    @Column(nullable = false, length = 50)
    var name: String,

    @Column(name = "owner_id", nullable = false)
    var ownerId: Long,

    @Column(nullable = false, length = 200)
    var description: String = "",
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    /** **초대 링크가 기본이다.** 처음부터 공개면 스팸 가입이 온다. */
    @Column(name = "open_join", nullable = false)
    var openJoin: Boolean = false

    @Column(name = "invite_token", nullable = false, length = 64)
    var inviteToken: String = newToken()

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null

    /** 링크를 새로 뽑는다. **옛 링크는 그 자리에서 죽는다** — 그것이 이 기능의 요점이다. */
    fun rotateInvite() {
        inviteToken = newToken()
    }

    fun delete() {
        deletedAt = Instant.now()
    }

    private companion object {
        val RANDOM = SecureRandom()

        fun newToken(): String = ByteArray(16)
            .also(RANDOM::nextBytes)
            .let(HexFormat.of()::formatHex)
    }
}
