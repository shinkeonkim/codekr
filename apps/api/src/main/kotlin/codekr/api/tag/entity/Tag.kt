package codekr.api.tag.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 문제의 알고리즘 분류 (#232).
 *
 * **카테고리와 다른 축이다.** 카테고리는 "무엇에 대한 문제인가"(SQL·OS·네트워크), 태그는
 * "어떤 기법으로 푸는가"(DP·이분 탐색)다. 둘의 경계를 지키지 않으면 같은 것이 두 곳에
 * 생긴다 — 그래서 **카테고리와 같은 이름의 태그는 만들지 않는다** (docs/02 §문제 분류).
 *
 * 태그는 **답의 일부다.** "이 문제는 DP" 를 알고 푸는 것과 모르고 푸는 것은 다른 문제라,
 * 어디에 어떻게 보이는지는 화면이 따로 정한다.
 */
@Entity
@Table(name = "tags")
class Tag(

    /** URL·필터 파라미터에 쓰는 이름. [name] 이 바뀌어도 링크가 살아남는다. */
    @Column(nullable = false, length = 60)
    var slug: String,

    @Column(nullable = false, length = 60)
    var name: String,

    /** 비슷한 태그가 둘 생기는 것을 막는 유일한 수단이다. 비워 두지 않는 편이 좋다. */
    @Column(length = 300)
    var description: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    lateinit var createdAt: Instant
        protected set
}
