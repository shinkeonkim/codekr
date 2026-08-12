package codekr.api.tag.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable

/** 문제에 붙은 태그 (#232). */
@Entity
@Table(name = "problem_tags")
class ProblemTag(
    @EmbeddedId
    val id: ProblemTagId,
) {
    val problemId: Long get() = id.problemId
    val tagId: Long get() = id.tagId
}

@Embeddable
data class ProblemTagId(
    @Column(name = "problem_id") val problemId: Long = 0,
    @Column(name = "tag_id") val tagId: Long = 0,
) : Serializable
