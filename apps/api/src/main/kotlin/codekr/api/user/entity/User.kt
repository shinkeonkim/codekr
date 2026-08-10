package codekr.api.user.entity

import codekr.api.common.entity.BaseTimeEntity
import codekr.api.submission.entity.SubmissionVisibility
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table

@Entity
@Table(name = "users")
class User(

    @Column(nullable = false, unique = true)
    val email: String,

    @Column(name = "password_hash", nullable = false)
    var passwordHash: String,

    @Column(nullable = false, unique = true)
    var nickname: String,

    roles: Set<UserRole> = setOf(UserRole.USER),

    /**
     * 제출할 때 기본으로 적용할 소스 공개 범위 (#104).
     *
     * 제출마다 바꾸는 것은 그대로 가능하다. 이 값은 **앞으로의 제출**에만 쓰인다 —
     * 이미 낸 제출의 범위를 소급해서 바꾸지 않는다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "default_submission_visibility", nullable = false, length = 20)
    var defaultSubmissionVisibility: SubmissionVisibility = SubmissionVisibility.PRIVATE,

    /**
     * 랭킹 목록에서 빠진다 (#58).
     *
     * 점수는 그대로 쌓인다 — 껐다 켤 때 기록이 사라지면 끄기가 되돌릴 수 없는 선택이 된다.
     */
    @Column(name = "ranking_opt_out", nullable = false)
    var rankingOptOut: Boolean = false,

    /**
     * 도달했던 최고 점수 (#58). 실력 티어는 이 값으로 정한다 — **강등이 없기 때문이다.**
     *
     * 현재 점수와 갈라질 수 있고, 그게 의도다. 재채점으로 점수가 내려가도 티어는 남는다.
     */
    @Column(name = "peak_score", nullable = false)
    var peakScore: Int = 0,

) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    /**
     * 가진 역할 (#103). 한 사람이 여럿을 가질 수 있다.
     *
     * EAGER 인 이유: 인증할 때마다 반드시 필요하고, 사람당 몇 개뿐이다.
     * LAZY 로 두면 토큰 발급 경로마다 초기화 시점을 신경 써야 한다.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = [JoinColumn(name = "user_id")])
    @Column(name = "role", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private val roleSet: MutableSet<UserRole> = roles.toMutableSet()

    val roles: Set<UserRole> get() = roleSet.toSet()

    /** 어드민 영역에 들어올 수 있는가. 어떤 역할을 가졌는지와는 별개다. */
    val isAdmin: Boolean get() = roleSet.any { it in UserRole.ADMIN_AREA }

    fun has(role: UserRole): Boolean = role in roleSet

    fun grant(role: UserRole) {
        roleSet.add(role)
    }

    /** USER 는 뺏을 수 없다. 뺏으면 로그인은 되는데 아무것도 못 하는 계정이 된다. */
    fun revoke(role: UserRole) {
        if (role != UserRole.USER) roleSet.remove(role)
    }
}
