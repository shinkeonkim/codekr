package codekr.api.user.entity

import codekr.api.common.entity.BaseTimeEntity
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
