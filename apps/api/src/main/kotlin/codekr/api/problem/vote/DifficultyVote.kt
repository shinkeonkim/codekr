package codekr.api.problem.vote

import codekr.api.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable

/** 난이도 투표 한 표 (#477). 사용자 × 문제 = 1표이고, **바꿀 수 있다.** */
@Entity
@Table(name = "problem_difficulty_votes")
@IdClass(DifficultyVoteId::class)
class DifficultyVote(

    @Id
    @Column(name = "problem_id")
    val problemId: Long,

    @Id
    @Column(name = "user_id")
    val userId: Long,

    /** 난이도 단계 (1~30). `difficulty_level` 과 같은 눈금이다. */
    @Column(nullable = false)
    var level: Int,

) : BaseTimeEntity()

data class DifficultyVoteId(val problemId: Long = 0, val userId: Long = 0) : Serializable
