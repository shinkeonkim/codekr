package codekr.api.ranking.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable

/**
 * 해결 기록을 **질의에서 참조하기 위한** 읽기 전용 매핑 (#239).
 *
 * 점수 표의 쓰기는 전부 `UserProblemScoreRepository` 가 SQL 로 한다 (#57) — 증분이 아니라
 * 원자료에서 다시 세는 방식이라 JPA 를 쓰지 않는다.
 *
 * 그런데 문제 목록의 "내가 푼 것 / 안 푼 것" 은 **문제 질의 안에서** 걸려야 한다.
 * Querydsl 이 참조하려면 엔티티가 있어야 해서, 읽기 전용으로 하나 둔다.
 * **여기로 저장하지 않는다.**
 */
@Entity
@Table(name = "user_problem_scores")
@IdClass(UserProblemScoreId::class)
class UserProblemScoreRef(
    @Id
    @Column(name = "user_id")
    val userId: Long = 0,

    @Id
    @Column(name = "problem_id")
    val problemId: Long = 0,
)

data class UserProblemScoreId(val userId: Long = 0, val problemId: Long = 0) : Serializable
