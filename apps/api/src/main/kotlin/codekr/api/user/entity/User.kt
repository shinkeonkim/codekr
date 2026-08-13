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
import java.time.Instant

@Entity
@Table(name = "users")
class User(

    @Column(nullable = false, unique = true)
    var email: String,

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
     * 도달했던 최고 점수 (#58). 실력 티어는 이 값으로 정한다 — **강등이 없기 때문이다.**
     *
     * 현재 점수와 갈라질 수 있고, 그게 의도다. 재채점으로 점수가 내려가도 티어는 남는다.
     */
    /**
     * 고른 화면 테마 (#274). **`null` 이 "고르지 않음" 이다.**
     *
     * 기본값을 박으면 나중에 기본값을 바꿀 때 이미 저장된 사람들이 그것을 이긴다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "theme", length = 16)
    var theme: UserTheme? = null,

    @Column(name = "peak_score", nullable = false)
    var peakScore: Int = 0,

    /**
     * 아바타 오브젝트 키 (#116). null 이면 올리지 않은 것이다.
     *
     * **URL 이 아니라 키를 저장한다.** 저장소 주소나 서빙 경로가 바뀌면 URL 은 모든 행을
     * 고쳐야 하고, 그 사이의 값은 깨진 링크가 된다.
     */
    @Column(name = "avatar_key", length = 120)
    var avatarKey: String? = null,

    /**
     * 소개 문구 (#310). 본인이 쓰고 프로필을 여는 누구에게나 보인다.
     *
     * **마크다운이 아니다.** 소개는 한두 줄이고, 마크다운을 열면 제목·목록·이미지가
     * 들어와 프로필 상단의 모양이 사람마다 달라진다. 줄바꿈만 살린다.
     */
    var bio: String? = null,

    /**
     * 이메일을 확인한 시각 (#233). null 이면 아직 확인하지 않은 것이다.
     *
     * **기존 계정은 인증된 것으로 본다** (마이그레이션에서 가입 시각을 넣는다) —
     * 전부 미인증으로 두면 인증 요구를 켜는 순간 지금 쓰고 있는 사람들이 다 막힌다.
     */
    @Column(name = "email_verified_at")
    var emailVerifiedAt: Instant? = null,


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

    /** 탈퇴한 시각 (#140). null 이면 쓰고 있는 계정이다. */
    @Column(name = "withdrawn_at")
    var withdrawnAt: Instant? = null
        protected set

    val isWithdrawn: Boolean get() = withdrawnAt != null

    /**
     * 탈퇴한다 (#140).
     *
     * **글과 댓글의 작성자 참조는 그대로 둔다.** 끊으면 집계가 함께 깨지고 되돌릴 수 없다.
     * 대신 **닉네임과 이메일을 익명 값으로 덮어쓴다** — 개인정보를 남기지 않는 것이
     * 탈퇴의 뜻이다.
     *
     * 덮어쓰므로 닉네임은 다시 쓸 수 있게 된다. 나간 사람이 닉네임을 영구 점유하지 않는다.
     */
    fun withdraw(now: Instant = Instant.now()) {
        if (isWithdrawn) return
        withdrawnAt = now
        // 되돌릴 수 없다. 유예 기간을 두지 않기로 했으므로 여기서 바로 지운다.
        email = "withdrawn+${'$'}id@codekr.invalid"
        nickname = "탈퇴회원${'$'}id"
        // 로그인을 막는 것과 별개로, 남은 비밀번호 해시도 쓸모가 없어야 한다.
        passwordHash = ""
        avatarKey = null
        // **가장 개인적인 내용이 들어갈 곳**이다. 탈퇴가 지울 것에 반드시 들어간다.
        bio = null
    }

    fun verifyEmail(now: Instant) {
        if (emailVerifiedAt == null) emailVerifiedAt = now
    }

    fun has(role: UserRole): Boolean = role in roleSet

    fun grant(role: UserRole) {
        roleSet.add(role)
    }

    /** USER 는 뺏을 수 없다. 뺏으면 로그인은 되는데 아무것도 못 하는 계정이 된다. */
    fun revoke(role: UserRole) {
        if (role != UserRole.USER) roleSet.remove(role)
    }
}
